package no.nav.amt.api.paamelding.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
