package no.nav.amt.lib.models.deltaker.internalapis.paamelding.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
