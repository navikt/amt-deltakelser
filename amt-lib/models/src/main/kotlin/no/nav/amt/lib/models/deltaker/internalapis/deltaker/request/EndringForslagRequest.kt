package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import java.util.UUID

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}
