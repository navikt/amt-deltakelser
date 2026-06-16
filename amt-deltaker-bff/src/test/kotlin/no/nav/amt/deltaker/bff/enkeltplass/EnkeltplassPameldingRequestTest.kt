package no.nav.amt.deltaker.bff.enkeltplass

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import org.junit.jupiter.api.Test

class EnkeltplassPameldingRequestTest {
    @Test
    fun `validate - skal returnere feil hvis beskrivelse er tom`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "",
            arrangorUnderenhet = "",
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
        )

        assertInvalidResult(request.validate(), "Beskrivelse kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis arrangorUnderenhet er tom`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            arrangorUnderenhet = "",
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
        )

        assertInvalidResult(request.validate(), "Arrangør orgnummer kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis ugyldig arrangorOrgnummer`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            arrangorUnderenhet = "abc",
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
        )

        assertInvalidResult(request.validate(), "Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")
    }

    @Test
    fun `validate - skal returnere gyldig resultat hvis alle påkrevde felt er fylt ut`() {
        val request = EnkeltplassPameldingRequest(
            beskrivelse = "~beskrivelse~",
            arrangorUnderenhet = "987654321",
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
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
