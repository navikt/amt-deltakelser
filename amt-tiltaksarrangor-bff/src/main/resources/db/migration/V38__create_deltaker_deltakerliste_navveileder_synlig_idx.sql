DROP INDEX CONCURRENTLY IF EXISTS deltaker_deltakerliste_synlig_idx;

CREATE INDEX CONCURRENTLY deltaker_deltakerliste_navveileder_synlig_idx
    ON deltaker(deltakerliste_id, navveileder_id)
    WHERE skjult_dato IS NULL;