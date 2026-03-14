CREATE TABLE ansatt
(
    id          uuid PRIMARY KEY,
    personident varchar                  not null UNIQUE,
    fornavn     varchar                  not null,
    mellomnavn  varchar,
    etternavn   varchar                  not null,
    modified_at timestamp with time zone not null default current_timestamp
);

CREATE TABLE ansatt_rolle
(
    ansatt_id   uuid                     not null references ansatt (id) on delete cascade,
    arrangor_id uuid                     not null,
    rolle       varchar                  not null,
    modified_at timestamp with time zone not null default current_timestamp,
    PRIMARY KEY (ansatt_id, arrangor_id, rolle)
);

CREATE TABLE koordinator_deltakerliste
(
    ansatt_id        uuid                     not null references ansatt (id) on delete cascade,
    deltakerliste_id uuid                     not null,
    modified_at      timestamp with time zone not null default current_timestamp,
    PRIMARY KEY (ansatt_id, deltakerliste_id)
);

CREATE TABLE veileder_deltaker
(
    ansatt_id    uuid                     not null references ansatt (id) on delete cascade,
    deltaker_id  uuid                     not null,
    veiledertype varchar                  not null,
    modified_at  timestamp with time zone not null default current_timestamp,
    PRIMARY KEY (ansatt_id, deltaker_id)
);