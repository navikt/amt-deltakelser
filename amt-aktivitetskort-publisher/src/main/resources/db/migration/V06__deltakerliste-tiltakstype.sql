alter table deltakerliste rename column tiltakstype to tiltaksnavn;
alter table deltakerliste add column tiltakstype varchar default 'UKJENT';
