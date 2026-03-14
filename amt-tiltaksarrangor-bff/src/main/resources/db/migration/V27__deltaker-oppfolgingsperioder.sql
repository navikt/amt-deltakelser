ALTER TABLE deltaker
    ADD COLUMN oppfolgingsperioder jsonb not null default '[]';

