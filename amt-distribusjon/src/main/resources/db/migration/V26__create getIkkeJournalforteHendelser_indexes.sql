-- for å optimalisere del #2 av spørring for getIkkeJournalforteHendelser
CREATE INDEX IF NOT EXISTS hendelse_id_created_at_dist_idx ON hendelse (id, created_at)
    WHERE distribusjonskanal NOT IN ('DITT_NAV', 'SDP');

-- for å optimalisere del #1 av spørring for getIkkeJournalforteHendelser
CREATE INDEX IF NOT EXISTS journalforingstatus_journalfores_idx ON journalforingstatus (hendelse_id)
    WHERE
        journalpost_id IS NULL
        AND kan_ikke_journalfores IS NOT TRUE;

-- for å optimalisere del #2 av spørring for getIkkeJournalforteHendelser
CREATE INDEX IF NOT EXISTS journalforingstatus_distribueres_idx ON journalforingstatus (hendelse_id)
    WHERE
        bestillingsid IS NULL
        AND kan_ikke_distribueres IS NOT TRUE;
