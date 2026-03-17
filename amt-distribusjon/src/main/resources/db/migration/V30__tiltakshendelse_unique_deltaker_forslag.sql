DROP INDEX IF EXISTS tiltakshendelse_forslag_id_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tiltakshendelse_forslag_unique_id_idx ON tiltakshendelse (forslag_id);
