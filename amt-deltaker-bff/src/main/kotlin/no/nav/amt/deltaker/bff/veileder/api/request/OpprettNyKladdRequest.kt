package no.nav.amt.deltaker.bff.veileder.api.request

import java.util.UUID

data class OpprettNyKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
