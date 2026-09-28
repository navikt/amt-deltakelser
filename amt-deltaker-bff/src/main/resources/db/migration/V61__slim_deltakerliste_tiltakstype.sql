-- Slanker persisterte tiltakskoordinator-tabeller til feltene bff faktisk bruker.
-- deltakerliste-stien leser kun status, slutt_dato, oppstart, pameldingstype, arrangor og
-- tiltakstype-id; resten var kun persistert uten å bli lest.

ALTER TABLE deltakerliste
    DROP COLUMN IF EXISTS navn,
    DROP COLUMN IF EXISTS start_dato,
    DROP COLUMN IF EXISTS apent_for_pamelding,
    DROP COLUMN IF EXISTS antall_plasser,
    DROP COLUMN IF EXISTS oppmote_sted;

ALTER TABLE tiltakstype
    DROP COLUMN IF EXISTS innsatsgrupper,
    DROP COLUMN IF EXISTS innhold;
