ALTER TABLE deltakerliste
    ADD COLUMN gjennomforingstype TEXT,
    ADD COLUMN status             TEXT,
    ADD COLUMN oppstart           TEXT,
    ADD COLUMN pameldingstype     TEXT,
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

