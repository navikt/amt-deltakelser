DROP INDEX IF EXISTS journalforingstatus_distribueres_idx2;

-- for å optimalisere del #2 av spørring for getIkkeJournalforteHendelser
CREATE INDEX IF NOT EXISTS journalforingstatus_distribueres_idx3
    ON journalforingstatus (hendelse_id)
    WHERE bestillingsid IS NULL
        AND kan_ikke_distribueres IS NOT TRUE
        AND NOT (
                journalpost_id IS NULL
                AND kan_ikke_journalfores IS NOT TRUE
            );