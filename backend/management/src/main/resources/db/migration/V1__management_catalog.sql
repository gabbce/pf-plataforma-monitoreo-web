CREATE TABLE machine_type (
    type_id TEXT PRIMARY KEY,
    model TEXT NOT NULL
);

CREATE TABLE machine_instance (
    instance_id TEXT PRIMARY KEY,
    type_id TEXT NOT NULL REFERENCES machine_type,
    UNIQUE (instance_id, type_id)
);

CREATE TABLE subsystem_definition (
    type_id TEXT NOT NULL REFERENCES machine_type,
    subsystem_id TEXT NOT NULL,
    name TEXT NOT NULL,
    PRIMARY KEY (type_id, subsystem_id)
);

CREATE TABLE record_definition (
    type_id TEXT NOT NULL REFERENCES machine_type,
    definition_id TEXT NOT NULL,
    subsystem_id TEXT,
    kind TEXT NOT NULL CHECK (kind IN ('DATA', 'STATE', 'EVENT', 'ALERT_TRANSITION')),
    name TEXT NOT NULL,
    PRIMARY KEY (type_id, definition_id),
    UNIQUE (type_id, definition_id, kind),
    FOREIGN KEY (type_id, subsystem_id) REFERENCES subsystem_definition
);

CREATE TABLE data_definition (
    type_id TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'DATA' CHECK (kind = 'DATA'),
    classification TEXT NOT NULL CHECK (classification IN ('ACQUIRED', 'DERIVED')),
    value_type TEXT NOT NULL,
    unit TEXT NOT NULL,
    PRIMARY KEY (type_id, definition_id),
    FOREIGN KEY (type_id, definition_id, kind) REFERENCES record_definition (type_id, definition_id, kind)
);

CREATE TABLE data_source (
    instance_id TEXT NOT NULL REFERENCES machine_instance,
    source_id TEXT PRIMARY KEY,
    origin TEXT NOT NULL CHECK (origin IN ('SENSOR', 'SIGNAL', 'FUNCTION')),
    reference TEXT NOT NULL,
    UNIQUE (instance_id, source_id)
);

CREATE TABLE data_source_binding (
    instance_id TEXT NOT NULL,
    type_id TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    source_id TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    PRIMARY KEY (instance_id, definition_id, valid_from),
    FOREIGN KEY (instance_id, type_id) REFERENCES machine_instance (instance_id, type_id),
    FOREIGN KEY (type_id, definition_id) REFERENCES data_definition,
    FOREIGN KEY (instance_id, source_id) REFERENCES data_source (instance_id, source_id),
    CHECK (isfinite(valid_from) AND (valid_until IS NULL OR (isfinite(valid_until) AND valid_until > valid_from))),
    EXCLUDE USING GIST (
        instance_id public.gist_text_ops WITH =,
        definition_id public.gist_text_ops WITH =,
        tstzrange(valid_from, valid_until, '[)') WITH &&
    )
);

CREATE TABLE audit_record (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_type TEXT NOT NULL CHECK (actor_type = 'SISTEMA'),
    actor_reference TEXT NOT NULL,
    action TEXT NOT NULL,
    element_type TEXT NOT NULL,
    element_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
