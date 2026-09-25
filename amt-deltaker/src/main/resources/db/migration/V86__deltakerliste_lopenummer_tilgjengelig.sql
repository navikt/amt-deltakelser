-- amt-deltaker leser lopenummer og tilgjengelig_fom fra Mulighetsrommet, men lagret dem ikke tidligere.
-- De trengs nå for å kunne dele komplett gjennomføringsdata på amt.gjennomforing-intern-topicet, også når en
-- gjennomføring må reproduseres (f.eks. når tilhørende tiltakstype endres).
ALTER TABLE deltakerliste
    ADD COLUMN lopenummer      TEXT,
    ADD COLUMN tilgjengelig_fom DATE;
