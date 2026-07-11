package no.nav.amt.lib.models.deltaker

data class Innhold(
    val tekst: String, // Tekst som vises på innholdskoden i frontend(kommer fra tiltaket)
    val innholdskode: String,
    val valgt: Boolean,
    val beskrivelse: String?,
) {
    val erFritekstInnholdsElement = innholdskode == INNHOLDSKODE_ANNET

    companion object {
        const val INNHOLDSKODE_ANNET = "annet"

        fun createFritekstInnhold(beskrivelse: String) = Innhold(
            tekst = "Annet",
            innholdskode = INNHOLDSKODE_ANNET,
            valgt = true,
            beskrivelse = beskrivelse,
        )
    }
}
