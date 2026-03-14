ALTER TABLE deltaker
    ADD COLUMN adressebeskyttet BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE deltaker
    ALTER COLUMN adressebeskyttet DROP DEFAULT;