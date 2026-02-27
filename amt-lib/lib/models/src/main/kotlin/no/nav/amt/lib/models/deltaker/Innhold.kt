package no.nav.amt.lib.models.deltaker

data class Innhold(
    val tekst: String,
    val innholdskode: String,
    val valgt: Boolean,
    val beskrivelse: String?,
)
