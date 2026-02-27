alter table deltaker_samtykke rename to vedtak;
alter table vedtak rename column godkjent to fattet;
alter table vedtak rename column deltaker_ved_samtykke to deltaker_ved_vedtak;
alter table vedtak rename column godkjent_av_nav to fattet_av_nav;
