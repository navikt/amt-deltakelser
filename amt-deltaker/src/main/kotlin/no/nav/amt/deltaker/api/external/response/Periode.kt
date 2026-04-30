package no.nav.amt.deltaker.api.external.response

import java.time.LocalDate

data class Periode(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
)
