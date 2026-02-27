alter table deltaker_endring
    add column forslag_id uuid references forslag;