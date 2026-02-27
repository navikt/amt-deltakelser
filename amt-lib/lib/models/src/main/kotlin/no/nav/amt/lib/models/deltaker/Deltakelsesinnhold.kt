package no.nav.amt.lib.models.deltaker

data class Deltakelsesinnhold(
    val ledetekst: String?,
    val innhold: List<Innhold>,
)
