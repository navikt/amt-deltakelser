alter table journalforingstatus add column modified_at timestamp with time zone default current_timestamp not null;

alter table journalforingstatus alter column journalpost_id drop not null;