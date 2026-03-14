CREATE INDEX CONCURRENTLY IF NOT EXISTS veileder_deltaker_deltaker_id_idx ON veileder_deltaker (deltaker_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS koordinator_deltakerliste_deltakerliste_id_idx ON koordinator_deltakerliste (deltakerliste_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS deltaker_deltakerliste_id_idx ON deltaker (deltakerliste_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS endringsmelding_deltaker_id_idx ON endringsmelding (deltaker_id);