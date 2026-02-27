create table deltaker_samtykke
(
    id                    uuid  not null primary key,
    deltaker_id           uuid  not null references deltaker,
    godkjent              timestamp with time zone,
    gyldig_til            timestamp with time zone,
    deltaker_ved_samtykke jsonb not null,
    godkjent_av_nav       jsonb,
    created_at            timestamp with time zone default CURRENT_TIMESTAMP,
    modified_at           timestamp with time zone default CURRENT_TIMESTAMP
)
