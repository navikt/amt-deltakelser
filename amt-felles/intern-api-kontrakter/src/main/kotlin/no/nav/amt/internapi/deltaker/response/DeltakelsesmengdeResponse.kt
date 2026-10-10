package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import java.time.LocalDate

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
) {
    constructor(deltakelsesmengde: Deltakelsesmengde) : this(
        deltakelsesprosent = deltakelsesmengde.deltakelsesprosent,
        dagerPerUke = deltakelsesmengde.dagerPerUke,
        gyldigFra = deltakelsesmengde.gyldigFra,
    )
}
