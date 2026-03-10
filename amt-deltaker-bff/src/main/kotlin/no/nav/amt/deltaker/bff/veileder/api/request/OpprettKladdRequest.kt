package no.nav.amt.deltaker.bff.veileder.api.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
