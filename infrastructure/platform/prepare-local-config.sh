#!/bin/sh
set -eu

cd "$(dirname "$0")/../.."
umask 077

runtime=.runtime/platform
install -d -m 0700 "$runtime" "$runtime/secrets"

for name in postgres-password management-db-password mongo-password records-mongo-password records-to-management-token; do
    secret="$runtime/secrets/$name"
    if [ ! -e "$secret" ]; then
        (set -C; openssl rand -hex 32 > "$secret")
    fi
    if [ ! -s "$secret" ]; then
        printf 'Archivo de secreto vacío: %s\n' "$secret" >&2
        exit 1
    fi
    # Containers read their mounted files after dropping privileges.
    chmod 0444 "$secret"
done
