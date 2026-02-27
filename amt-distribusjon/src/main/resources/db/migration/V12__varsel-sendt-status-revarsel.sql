alter table varsel
   add column status varchar not null default 'UTFORT',
   add column revarsel_for_varsel UUID,
   add column revarsles timestamp with time zone;

update varsel
set status = 'AKTIV'
where aktiv_til at time zone 'UTC'> current_timestamp at time zone 'UTC';

alter table varsel alter column status drop default;

alter table varsel drop column er_sendt;

alter table varsel rename column skal_varsle_eksternt to er_eksternt_varsel;
