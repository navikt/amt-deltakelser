CREATE INDEX IF NOT EXISTS vurdering_deltaker_id_fk_idx ON vurdering (deltaker_id);

DROP INDEX IF EXISTS deltaker_status_type_active_idx2;