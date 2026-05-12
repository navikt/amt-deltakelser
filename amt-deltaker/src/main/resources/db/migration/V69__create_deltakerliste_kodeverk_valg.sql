CREATE TABLE IF NOT EXISTS deltakerliste_kodeverk_valg
(
    deltakerliste_id uuid primary key references deltakerliste (id),
    kodeverk_valg uuid[] not null
);