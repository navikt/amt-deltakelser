ALTER TABLE deltakerliste ADD COLUMN gjennomforingstype VARCHAR;

UPDATE deltakerliste
SET gjennomforingstype =
    CASE
        WHEN start_dato IS NOT NULL THEN 'Gruppe'
        ELSE 'Enkeltplass'
    END
WHERE gjennomforingstype IS NULL;

ALTER TABLE deltakerliste ALTER COLUMN gjennomforingstype SET NOT NULL;