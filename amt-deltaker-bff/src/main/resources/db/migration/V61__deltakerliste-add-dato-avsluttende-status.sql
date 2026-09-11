ALTER TABLE deltakerliste
    ADD COLUMN IF NOT EXISTS dato_avsluttende_status DATE;

UPDATE deltakerliste
    SET dato_avsluttende_status = CASE
                                  WHEN status = 'AVSLUTTET' THEN slutt_dato
                                  WHEN status = 'AVRUTT' THEN slutt_dato
                                  WHEN status = 'AVLYST' THEN start_dato
    END
WHERE
    deltakerliste.dato_avsluttende_status IS NULL
    AND status IN ('AVSLUTTET', 'AVBRUTT', 'AVLYST');