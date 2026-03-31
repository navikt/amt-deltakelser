package no.nav.amt.internapi.enkeltplass

import java.time.LocalDate

data class EnkeltplassPameldingRequest(
    val beskrivelse: String,
    val prisinformasjon: String,
    val arrangorOrgnummer: String,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
)
