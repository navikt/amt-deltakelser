package no.nav.amt.deltaker.bff.enkeltplass

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import org.junit.jupiter.api.Test

class EnkeltplassPameldingRequestTest {
    @Test
    fun `validate - skal returnere feil hvis beskrivelse er tom`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "",
            prisinformasjon = "",
            arrangorUnderenhet = "",
        )

        assertInvalidResult(request.validate(), "Beskrivelse kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis prisinformasjon er tom`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "",
            arrangorUnderenhet = "",
        )

        assertInvalidResult(request.validate(), "Prisinformasjon kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis arrangorUnderenhet er tom`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorUnderenhet = "",
        )

        assertInvalidResult(request.validate(), "Arrangør orgnummer kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis ugyldig arrangorOrgnummer`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorUnderenhet = "abc",
        )

        assertInvalidResult(request.validate(), "Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")
    }

    @Test
    fun `validate - skal returnere gyldig resultat hvis alle påkrevde felt er fylt ut`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorUnderenhet = "987654321",
        )

        request.validate().shouldBeInstanceOf<ValidationResult.Valid>()
    }

    companion object {
        private fun assertInvalidResult(
            result: ValidationResult,
            expected: String,
        ) {
            result.shouldBeInstanceOf<ValidationResult.Invalid>()
            result.reasons shouldContainAll listOf(expected)
        }
    }
}
