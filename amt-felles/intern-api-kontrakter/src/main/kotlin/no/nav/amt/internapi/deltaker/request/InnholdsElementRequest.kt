package no.nav.amt.internapi.deltaker.request

data class InnholdsElementRequest(
    val innholdskode: String, // Innholdselement kodeset
    val beskrivelse: String?, // Beskrivelse som kun finnes på "Annet" innholdselement
)
