ALTER TABLE innsok
    ADD COLUMN IF NOT EXISTS prisinformasjon_ved_innsok jsonb DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS dager_per_uke_ved_innsok INTEGER DEFAULT NULL;
