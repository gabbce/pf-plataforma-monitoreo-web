ALTER TABLE station_credential
    ADD COLUMN last_authenticated_contact_at TIMESTAMPTZ
    CHECK (last_authenticated_contact_at IS NULL OR isfinite(last_authenticated_contact_at));
