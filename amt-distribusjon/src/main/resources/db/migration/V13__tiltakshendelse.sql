create table tiltakshendelse
(
    id          uuid primary key,
    type        varchar                                            not null,
    deltaker_id uuid                                               not null,
    forslag_id  uuid,
    hendelser   uuid[]                                             not null default array []::uuid[],
    personident varchar                                            not null,
    aktiv       boolean                                            not null,
    tekst       varchar                                            not null,
    tiltakstype varchar                                            not null,
    created_at  timestamp with time zone default current_timestamp not null,
    modified_at timestamp with time zone default current_timestamp not null
);

create index tiltakshendelse_deltaker_id_idx on tiltakshendelse (deltaker_id);
create index tiltakshendelse_forslag_id_idx on tiltakshendelse (forslag_id);
create index tiltakshendelse_hendelser_gin_idx on tiltakshendelse using gin (hendelser);