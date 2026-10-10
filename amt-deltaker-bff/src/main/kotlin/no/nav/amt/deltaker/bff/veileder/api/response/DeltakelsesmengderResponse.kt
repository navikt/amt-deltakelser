package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
) {
    constructor(model: DeltakelsesmengderResponse) : this(
        nesteDeltakelsesmengde = model.nesteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
        sisteDeltakelsesmengde = model.sisteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
    )
}
