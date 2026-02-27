create table nav_bruker
(
    personId    uuid primary key                                   not null,
    personident varchar                                            not null,
    fornavn     varchar                                            not null,
    mellomnavn  varchar,
    etternavn   varchar                                            not null,
    created_at  timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index nav_bruker_personident_idx on nav_bruker (personident);
