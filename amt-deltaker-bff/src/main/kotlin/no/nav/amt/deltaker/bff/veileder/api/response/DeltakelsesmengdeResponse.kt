package no.nav.amt.deltaker.bff.veileder.api.response

import java.time.LocalDate
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse as InternDeltakelsesmengdeResponse

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float?,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
) {
    constructor(model: InternDeltakelsesmengdeResponse) : this(
        deltakelsesprosent = model.deltakelsesprosent,
        dagerPerUke = model.dagerPerUke,
        gyldigFra = model.gyldigFra,
    )
}
