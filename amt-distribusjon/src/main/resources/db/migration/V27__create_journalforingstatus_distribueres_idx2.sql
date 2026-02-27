DROP INDEX IF EXISTS journalforingstatus_distribueres_idx;

-- for å optimalisere del #2 av spørring for getIkkeJournalforteHendelser
CREATE INDEX IF NOT EXISTS journalforingstatus_distribueres_idx2
    ON journalforingstatus(hendelse_id)
    WHERE bestillingsid IS NULL
        AND kan_ikke_distribueres IS NOT TRUE
        AND journalpost_id IS NOT NULL;