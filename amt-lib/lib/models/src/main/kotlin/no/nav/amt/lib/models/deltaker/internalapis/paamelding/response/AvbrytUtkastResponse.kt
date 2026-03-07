package no.nav.amt.lib.models.deltaker.internalapis.paamelding.response

import java.util.UUID

// denne klassen kan slettes når amt-deltaker tar over låsing
data class AvbrytUtkastResponse(
    val deltakerIdSomSkalLaasesOpp: UUID?,
)
