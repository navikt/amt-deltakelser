CREATE TABLE arrangor
(
    id                     uuid PRIMARY KEY,
    navn                   varchar                  not null,
    organisasjonsnummer    varchar                  not null UNIQUE,
    overordnet_arrangor_id uuid,
    created_at             timestamp with time zone not null default current_timestamp,
    modified_at            timestamp with time zone not null default current_timestamp
);