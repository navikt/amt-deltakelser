ALTER TABLE prisinformasjon_godkjenning
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE prisinformasjon_godkjenning AS pg
SET
    created_at = p.created_at,
    modified_at = p.modified_at
FROM prisinformasjon AS p
WHERE pg.prisinformasjon_id = p.id;