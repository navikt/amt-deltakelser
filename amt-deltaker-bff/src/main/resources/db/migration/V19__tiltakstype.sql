create table tiltakstype
(
    id          uuid primary key,
    navn        varchar                  not null,
    type        varchar                  not null,
    innhold     jsonb,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp,
    unique (type)
);

create index tiltakstype_type_idx on tiltakstype (type);