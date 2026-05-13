package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import java.time.LocalDate

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
) {
    companion object {
        fun fromDeltakelsesmengde(deltakelsesmengde: Deltakelsesmengde) = with(deltakelsesmengde) {
            DeltakelsesmengdeResponse(
                deltakelsesprosent = deltakelsesprosent,
                dagerPerUke = dagerPerUke,
                gyldigFra = gyldigFra,
            )
        }
    }
}
