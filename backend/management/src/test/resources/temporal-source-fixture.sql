INSERT INTO machine_type (type_id, model)
VALUES ('embolsadora', 'Embolsadora de forrajes'), ('other-type', 'Otro tipo de prueba');

INSERT INTO machine_instance (instance_id, type_id)
VALUES ('000123', 'embolsadora'), ('000124', 'embolsadora');

INSERT INTO subsystem_definition (type_id, subsystem_id, name)
VALUES ('embolsadora', 'engine', 'Motor'), ('other-type', 'engine', 'Motor');

INSERT INTO record_definition (type_id, definition_id, subsystem_id, kind, name)
VALUES ('embolsadora', 'engine.rpm', 'engine', 'DATA', 'RPM del motor'),
       ('other-type', 'engine.rpm', 'engine', 'DATA', 'RPM del motor');

INSERT INTO data_definition (type_id, definition_id, classification, value_type, unit)
VALUES ('embolsadora', 'engine.rpm', 'ACQUIRED', 'NUMBER', 'rpm'),
       ('other-type', 'engine.rpm', 'ACQUIRED', 'NUMBER', 'rpm');

INSERT INTO data_source (instance_id, source_id, origin, reference)
VALUES ('000123', 'rpm-a', 'SENSOR', 'rpm-a'), ('000123', 'rpm-b', 'SENSOR', 'rpm-b'),
       ('000124', 'rpm-other', 'SENSOR', 'rpm-other');

INSERT INTO data_source_binding (instance_id, type_id, definition_id, source_id, valid_from, valid_until)
VALUES ('000123', 'embolsadora', 'engine.rpm', 'rpm-a', '2026-01-15T12:00:00Z', '2026-01-15T12:05:00Z'),
       ('000123', 'embolsadora', 'engine.rpm', 'rpm-b', '2026-01-15T12:05:00Z', NULL);
