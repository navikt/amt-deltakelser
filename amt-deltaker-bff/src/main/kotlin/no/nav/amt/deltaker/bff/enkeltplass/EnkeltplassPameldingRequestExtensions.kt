package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest

// Norske orgnr starter med '8' eller '9'. Utenlandske og test-orgnr følger ikke dette mønsteret.
// Krever derfor kun at orgnr inneholder 9 siffer.
val ORGNUMMER_REGEX = Regex("^\\d{9}$")

fun EnkeltplassPameldingRequest.validate(): ValidationResult = when {
    beskrivelse.isBlank() -> ValidationResult.Invalid("Beskrivelse kan ikke være tom")
    arrangorUnderenhet.isBlank() -> ValidationResult.Invalid("Arrangør orgnummer kan ikke være tom")
    !ORGNUMMER_REGEX.matches(
        arrangorUnderenhet,
    ) -> ValidationResult.Invalid("Organisasjonsnummeret må inneholde 9 siffer")

    else -> {
        prisinformasjon
            .validate()
            .takeIf { it.isNotEmpty() }
            ?.let { return ValidationResult.Invalid(it) }

        ValidationResult.Valid
    }
}
