package no.nav.amt.internapi.paamelding.request

import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest

data class KladdRequest(
    val innhold: List<InnholdsElementRequest>,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
)
