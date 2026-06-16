package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest

val ORGNUMMER_REGEX = Regex("^[89]\\d{8}$")

fun EnkeltplassPameldingRequest.validate(): ValidationResult = when {
    beskrivelse.isBlank() -> ValidationResult.Invalid("Beskrivelse kan ikke være tom")

    // TODO prisinformasjon.isBlank() -> ValidationResult.Invalid("Prisinformasjon kan ikke være tom")
    arrangorUnderenhet.isBlank() -> ValidationResult.Invalid("Arrangør orgnummer kan ikke være tom")
    !ORGNUMMER_REGEX.matches(
        arrangorUnderenhet,
    ) -> ValidationResult.Invalid("Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")

    else -> ValidationResult.Valid
}
