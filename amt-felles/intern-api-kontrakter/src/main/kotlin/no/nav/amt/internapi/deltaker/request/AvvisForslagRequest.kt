package no.nav.amt.internapi.deltaker.request

data class AvvisForslagRequest(
    val begrunnelse: String,
    val avvistAvAnsattIdent: String,
    val avvistAvEnhet: String,
)
