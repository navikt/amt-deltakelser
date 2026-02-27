package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response


data class ArrangorResponse(
    // Dette er navnet som skal brukes for alle praktiske formål
    // Men ikke nødvendigvis navnet til underenheten som svarer til orgnr
    val navn: String,
    val organisasjonsnummer: String, // Fjerne orgnr?
)