CREATE TABLE nav_ansatt
(
    id          uuid primary key,
    nav_ident   varchar                  not null,
    navn        varchar                  not null,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp,
    unique (nav_ident)
);

create index nav_ansatt_nav_ident_idx on nav_ansatt (nav_ident);