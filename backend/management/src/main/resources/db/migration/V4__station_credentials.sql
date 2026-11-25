CREATE TABLE station_credential (
    credential_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id TEXT NOT NULL REFERENCES machine_instance,
    producer_id TEXT NOT NULL CHECK (producer_id ~ '[^[:space:]]'),
    token_digest BYTEA NOT NULL UNIQUE CHECK (octet_length(token_digest) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (isfinite(created_at)),
    revoked_at TIMESTAMPTZ,
    UNIQUE (instance_id, producer_id),
    CHECK (revoked_at IS NULL OR (isfinite(revoked_at) AND revoked_at >= created_at))
);

CREATE TABLE station_credential_operation (
    credential_id BIGINT NOT NULL REFERENCES station_credential,
    operation TEXT NOT NULL CHECK (operation IN (
        'RECORDS_WRITE', 'CONFIGURATION_READ', 'CONFIGURATION_RESULTS_WRITE'
    )),
    PRIMARY KEY (credential_id, operation)
);
