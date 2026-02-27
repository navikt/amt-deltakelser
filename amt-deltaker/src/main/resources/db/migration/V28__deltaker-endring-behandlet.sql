alter table deltaker_endring add column behandlet timestamp with time zone;
alter table deltaker_endring add column endret timestamp with time zone;

update deltaker_endring set behandlet = modified_at, endret = modified_at;
