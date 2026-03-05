package no.nav.amt.deltaker.bff.deltaker.api.model

import java.util.UUID

data class OpprettNyKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)
