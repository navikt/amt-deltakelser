package no.nav.amt.deltaker.deltaker.api.deltaker

import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringForslagRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import java.util.UUID

fun EndringRequest.getForslagId(): UUID? = if (this is EndringForslagRequest) {
    this.forslagId
} else {
    null
}
