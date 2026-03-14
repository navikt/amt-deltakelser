CREATE INDEX CONCURRENTLY deltaker_deltakerliste_synlig_idx
    ON deltaker (deltakerliste_id)
    WHERE skjult_dato IS NULL;