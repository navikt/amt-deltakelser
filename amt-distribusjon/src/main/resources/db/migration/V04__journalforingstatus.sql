create table journalforingstatus
(
    hendelse_id    uuid primary key,
    journalpost_id varchar                                            not null,
    created_at     timestamp with time zone default current_timestamp not null
)