CREATE INDEX melding_deltaker_created_at_idx
    ON melding (deltaker_id, created_at DESC);