package no.nav.amt.deltaker.bff.deltaker.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold

data class Pamelding(
    val deltakelsesinnhold: Deltakelsesinnhold,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Float?,
    val dagerPerUke: Float?,
    val endretAv: String,
    val endretAvEnhet: String,
)
