CREATE TABLE web_user (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identifier TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    active BOOLEAN NOT NULL,
    password_hash TEXT NOT NULL
);

CREATE TABLE web_role (
    role_id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE web_permission (
    permission_id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE web_user_role (
    user_id BIGINT NOT NULL REFERENCES web_user,
    role_id TEXT NOT NULL REFERENCES web_role,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE web_role_permission (
    role_id TEXT NOT NULL REFERENCES web_role,
    permission_id TEXT NOT NULL REFERENCES web_permission,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE web_machine_association (
    user_id BIGINT NOT NULL REFERENCES web_user,
    instance_id TEXT NOT NULL REFERENCES machine_instance,
    link_type TEXT NOT NULL CHECK (link_type IN ('OWNER', 'OPERATOR')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    PRIMARY KEY (user_id, instance_id, link_type, valid_from),
    CHECK (isfinite(valid_from) AND (valid_until IS NULL OR (isfinite(valid_until) AND valid_until > valid_from))),
    EXCLUDE USING GIST (
        user_id public.gist_int8_ops WITH =,
        instance_id public.gist_text_ops WITH =,
        link_type public.gist_text_ops WITH =,
        tstzrange(valid_from, valid_until, '[)') WITH &&
    )
);

CREATE INDEX web_machine_association_instance_idx ON web_machine_association (instance_id);

CREATE TABLE web_login_context (
    context_digest TEXT PRIMARY KEY CHECK (context_digest ~ '^[0-9a-f]{64}$'),
    csrf_token TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at))
);

CREATE INDEX web_login_context_expiry_idx ON web_login_context (expires_at);

CREATE TABLE web_session (
    session_digest TEXT PRIMARY KEY CHECK (session_digest ~ '^[0-9a-f]{64}$'),
    user_id BIGINT NOT NULL REFERENCES web_user,
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at))
);

CREATE INDEX web_session_expiry_idx ON web_session (expires_at);
CREATE INDEX web_session_user_idx ON web_session (user_id);
