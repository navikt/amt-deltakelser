package no.nav.amt.lib.models.deltaker

import com.fasterxml.jackson.annotation.JsonIgnore

data class Deltakelsesinnhold(
    val ledetekst: String?,
    val innhold: List<Innhold>,
) {
    @JsonIgnore
    fun getAnnetFritekstBeskrivelse(): String? = innhold
        .filter { it.valgt }
        .firstOrNull { it.erFritekstInnholdsElement }
        ?.takeUnless { it.beskrivelse.isNullOrBlank() }
        ?.beskrivelse
}
