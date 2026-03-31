package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest

val ORGNUMMER_REGEX = Regex("^[89]\\d{8}$")

fun MeldPaaDirekteEnkeltplassRequest.validate(): ValidationResult = when {
    beskrivelse.isBlank() -> ValidationResult.Invalid("Beskrivelse kan ikke være tom")
    prisinformasjon.isBlank() -> ValidationResult.Invalid("Prisinformasjon kan ikke være tom")
    arrangorOrgnummer.isBlank() -> ValidationResult.Invalid("Arrangør orgnummer kan ikke være tom")
    !ORGNUMMER_REGEX.matches(
        arrangorOrgnummer,
    ) -> ValidationResult.Invalid("Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")

    else -> ValidationResult.Valid
}
