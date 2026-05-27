package no.nav.amt.lib.models.deltaker

data class Innhold(
    val tekst: String, // Tekst som vises på innholdskoden i frontend(kommer fra tiltaket)
    val innholdskode: String,
    val valgt: Boolean,
    val beskrivelse: String?,
) {
    val erFritekstInnholdsElement = innholdskode == "annet"

    companion object {
        fun createFritekstInnhold(beskrivelse: String) = Innhold(
            tekst = "Annet",
            innholdskode = "annet",
            valgt = true,
            beskrivelse = beskrivelse,
        )
    }
}
