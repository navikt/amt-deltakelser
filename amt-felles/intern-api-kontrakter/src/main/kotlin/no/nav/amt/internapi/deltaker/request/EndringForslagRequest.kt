package no.nav.amt.internapi.deltaker.request

import java.util.UUID

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}
