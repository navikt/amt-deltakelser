package no.nav.amt.distribusjon.journalforing.model

import no.nav.amt.distribusjon.hendelse.model.Hendelse

data class HendelseMedJournalforingstatus(
    val hendelse: Hendelse,
    val journalforingstatus: Journalforingstatus,
)
