package no.nav.amt.internapi.deltaker.response

import java.util.UUID

data class ArrangorResponse(
    // Dette er navnet som skal brukes for alle praktiske formål
    // Men ikke nødvendigvis navnet til underenheten som svarer til orgnr
    val navn: String,
    val organisasjonsnummer: String,
    val id: UUID,
)
