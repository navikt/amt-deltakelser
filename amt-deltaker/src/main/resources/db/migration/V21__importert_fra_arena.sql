create table importert_fra_arena
(
    deltaker_id         uuid references deltaker primary key,
    deltaker_ved_import jsonb                    not null,
    importert_dato      timestamp with time zone not null
);