#!/bin/sh
set -eu

export PGDATABASE=management PGCONNECT_TIMEOUT=5 PGREQUIREAUTH=scram-sha-256
export MANAGEMENT_DB_PASSWORD="$(cat /run/secrets/management_db_password)"
admin_password="$(cat /run/secrets/postgres_password)"
if [ -z "$admin_password" ] || [ -z "$MANAGEMENT_DB_PASSWORD" ] || [ "$admin_password" = "$MANAGEMENT_DB_PASSWORD" ]; then
    printf 'Se requieren contraseñas distintas y no vacías para PostgreSQL y Gestión.\n' >&2
    exit 1
fi

admin_psql() {
    PGPASSWORD="$admin_password" psql -X --no-password --username=postgres --set=ON_ERROR_STOP=1 "$@"
}

check_management_login() {
    PGPASSWORD="$MANAGEMENT_DB_PASSWORD" psql -X --no-password --username=management_app \
        --set=ON_ERROR_STOP=1 --quiet --output=/dev/null --command='SELECT 1'
}

role_exists="$(admin_psql --tuples-only --no-align --command="SELECT EXISTS (
    SELECT FROM pg_roles WHERE rolname = 'management_app'
)")"
if [ "$role_exists" = t ]; then
    check_management_login
fi

admin_psql --quiet <<'SQL'
\getenv app_password MANAGEMENT_DB_PASSWORD
BEGIN;
SET LOCAL search_path TO pg_catalog;
SELECT pg_advisory_xact_lock(158303, 2) AS locked \gset

SELECT NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'management_app') AS create_role \gset
\if :create_role
SET LOCAL password_encryption = 'scram-sha-256';
CREATE ROLE management_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS
    PASSWORD :'app_password';
CREATE SCHEMA management AUTHORIZATION management_app;
\endif

DO $bootstrap$
DECLARE
    app_role pg_roles%ROWTYPE;
    app_schema pg_namespace%ROWTYPE;
BEGIN
    SELECT * INTO app_role FROM pg_roles WHERE rolname = 'management_app';
    SELECT * INTO app_schema FROM pg_namespace WHERE nspname = 'management';

    IF NOT EXISTS (
        SELECT FROM pg_database
        WHERE datname = current_database() AND datdba = 'postgres'::regrole
    ) THEN
        RAISE EXCEPTION 'The management database must be owned by postgres';
    END IF;

    IF NOT EXISTS (
        SELECT FROM pg_namespace
        WHERE nspname = 'public' AND nspowner IN ('postgres'::regrole, 'pg_database_owner'::regrole)
    ) OR EXISTS (
        SELECT FROM pg_namespace, LATERAL aclexplode(
            COALESCE(nspacl, acldefault('n', nspowner))
        )
        WHERE nspname = 'public' AND privilege_type = 'CREATE'
          AND grantee NOT IN ('postgres'::regrole, 'pg_database_owner'::regrole)
    ) THEN
        RAISE EXCEPTION 'The public schema must be owned and writable only by the database administrator';
    END IF;

    IF app_schema.oid IS NULL OR (
        app_schema.nspowner <> app_role.oid OR NOT app_role.rolcanlogin OR app_role.rolsuper
        OR app_role.rolcreatedb OR app_role.rolcreaterole OR app_role.rolinherit
        OR app_role.rolreplication OR app_role.rolbypassrls OR app_role.rolconnlimit <> -1
        OR EXISTS (SELECT FROM pg_auth_members WHERE member = app_role.oid OR roleid = app_role.oid)
        OR NOT has_database_privilege(app_role.oid, current_database(), 'CONNECT')
        OR has_database_privilege(app_role.oid, current_database(), 'CREATE')
        OR NOT has_schema_privilege(app_role.oid, 'public', 'USAGE')
        OR has_schema_privilege(app_role.oid, 'public', 'CREATE')
        OR EXISTS (
            SELECT FROM aclexplode(COALESCE(app_schema.nspacl, acldefault('n', app_role.oid)))
            WHERE grantee <> app_role.oid
        )
        OR NOT has_schema_privilege(app_role.oid, app_schema.oid, 'USAGE')
        OR NOT has_schema_privilege(app_role.oid, app_schema.oid, 'CREATE')
        OR EXISTS (
            SELECT FROM pg_class
            WHERE relnamespace = app_schema.oid AND relowner <> app_role.oid
        )
    ) THEN
        RAISE EXCEPTION 'Incompatible management role, schema ownership or privileges';
    END IF;

    IF EXISTS (
        SELECT FROM pg_extension e
        JOIN pg_namespace n ON n.oid = e.extnamespace
        WHERE e.extname = 'btree_gist' AND (n.nspname <> 'public' OR e.extowner <> 'postgres'::regrole)
    ) THEN
        RAISE EXCEPTION 'Incompatible btree_gist extension ownership or schema';
    END IF;
END
$bootstrap$;

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
COMMIT;
SQL

check_management_login
