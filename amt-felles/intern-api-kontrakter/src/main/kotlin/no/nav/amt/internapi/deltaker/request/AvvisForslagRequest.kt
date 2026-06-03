package no.nav.amt.internapi.deltaker.request

import java.util.UUID

data class AvvisForslagRequest(
    val begrunnelse: String,
    val avvistAvAnsatt: UUID,
    val avvistAvEnhet: String,
)
