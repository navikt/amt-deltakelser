ALTER TABLE deltakerliste ADD COLUMN gjennomforingstype VARCHAR;

UPDATE deltakerliste SET gjennomforingstype = 'Gruppe' WHERE gjennomforingstype IS NULL;

ALTER TABLE deltakerliste ALTER COLUMN gjennomforingstype SET NOT NULL;