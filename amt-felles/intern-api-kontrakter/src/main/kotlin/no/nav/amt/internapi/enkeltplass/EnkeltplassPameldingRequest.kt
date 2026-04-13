package no.nav.amt.internapi.enkeltplass

import java.time.LocalDate

data class EnkeltplassPameldingRequest(
    val beskrivelse: String,
    val prisinformasjon: String,
    val arrangorUnderenhet: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
) {
    fun sanitized() = copy(
        beskrivelse = beskrivelse.sanitizeBeskrivelse(),
        prisinformasjon = prisinformasjon.sanitizePrisinformasjon(),
        arrangorUnderenhet = arrangorUnderenhet.sanitizeArrangorUnderenhet(),
    )
}
