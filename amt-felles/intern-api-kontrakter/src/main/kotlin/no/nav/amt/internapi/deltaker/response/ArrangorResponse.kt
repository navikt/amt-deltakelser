package no.nav.amt.internapi.deltaker.response

data class ArrangorResponse(
    // Dette er navnet som skal brukes for alle praktiske formål
    // Men ikke nødvendigvis navnet til underenheten som svarer til orgnr
    val navn: String,
    val organisasjonsnummer: String,
)
