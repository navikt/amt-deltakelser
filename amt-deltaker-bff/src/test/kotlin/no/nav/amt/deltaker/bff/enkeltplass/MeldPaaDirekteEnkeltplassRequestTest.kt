package no.nav.amt.deltaker.bff.enkeltplass

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest
import org.junit.jupiter.api.Test

class MeldPaaDirekteEnkeltplassRequestTest {
    @Test
    fun `validate - skal returnere feil hvis beskrivelse er tom`() {
        val request = MeldPaaDirekteEnkeltplassRequest(
            beskrivelse = "",
            prisinformasjon = "",
            arrangorOrgnummer = "",
        )

        assertInvalidResult(request.validate(), "Beskrivelse kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis prisinformasjon er tom`() {
        val request = MeldPaaDirekteEnkeltplassRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "",
            arrangorOrgnummer = "",
        )

        assertInvalidResult(request.validate(), "Prisinformasjon kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis arrangorOrgnummer er tom`() {
        val request = MeldPaaDirekteEnkeltplassRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorOrgnummer = "",
        )

        assertInvalidResult(request.validate(), "Arrangør orgnummer kan ikke være tom")
    }

    @Test
    fun `validate - skal returnere feil hvis ugyldig arrangorOrgnummer`() {
        val request = MeldPaaDirekteEnkeltplassRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorOrgnummer = "abc",
        )

        assertInvalidResult(request.validate(), "Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")
    }

    @Test
    fun `validate - skal returnere gyldig resultat hvis alle felt er fylt ut`() {
        val request = MeldPaaDirekteEnkeltplassRequest(
            beskrivelse = "~beskrivelse~",
            prisinformasjon = "~prisinfo~",
            arrangorOrgnummer = "987654321",
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
