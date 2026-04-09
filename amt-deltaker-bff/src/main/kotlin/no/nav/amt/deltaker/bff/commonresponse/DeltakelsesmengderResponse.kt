package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse?,
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse?,
) {
    companion object {
        fun fromDeltakelsesmengder(deltakelsesmengder: Deltakelsesmengder) = DeltakelsesmengderResponse(
            nesteDeltakelsesmengde = deltakelsesmengder.nesteGjeldende?.let { DeltakelsesmengdeResponse.fromDeltakelsesmengde(it) },
            sisteDeltakelsesmengde = deltakelsesmengder.lastOrNull()?.let { DeltakelsesmengdeResponse.fromDeltakelsesmengde(it) },
        )
    }
}
