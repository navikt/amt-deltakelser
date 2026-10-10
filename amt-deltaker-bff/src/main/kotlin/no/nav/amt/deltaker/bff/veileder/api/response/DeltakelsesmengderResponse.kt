package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse as InternDeltakelsesmengderResponse

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
) {
    constructor(model: InternDeltakelsesmengderResponse) : this(
        nesteDeltakelsesmengde = model.nesteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
        sisteDeltakelsesmengde = model.sisteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
    )
}
