package no.nav.amt.internapi.paamelding.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
