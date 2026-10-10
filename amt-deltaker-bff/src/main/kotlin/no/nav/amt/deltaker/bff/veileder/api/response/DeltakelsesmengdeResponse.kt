package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import java.time.LocalDate

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float?,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
) {
    constructor(model: DeltakelsesmengdeResponse) : this(
        deltakelsesprosent = model.deltakelsesprosent,
        dagerPerUke = model.dagerPerUke,
        gyldigFra = model.gyldigFra,
    )
}
