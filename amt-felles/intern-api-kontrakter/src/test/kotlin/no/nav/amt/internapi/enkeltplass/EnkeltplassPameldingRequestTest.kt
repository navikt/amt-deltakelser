@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.internapi.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto.Companion.MAX_LENGTH_TILLEGGSOPPLYSNINGER
import org.junit.jupiter.api.Test

class EnkeltplassPameldingRequestTest {
    @Test
    fun sanitized() {
        // Arrange
        val longString = "a".repeat(1000)
        val prisinformasjonInTest = PrisinformasjonDto.IngenKostnader(
            aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = longString,
        )

        // Act
        val sanitizedRequest = EnkeltplassPameldingRequest(
            beskrivelse = longString,
            prisinformasjon = prisinformasjonInTest,
            arrangorUnderenhet = longString,
            startdato = null,
            sluttdato = null,
        ).sanitized()

        // Assert
        assertSoftly(sanitizedRequest) {
            beskrivelse shouldBe longString.take(MAX_LENGTH_BESKRIVELSE)
            arrangorUnderenhet shouldBe longString.take(MAX_LENGTH_ARRANGOR_UNDERENHET)
            prisinformasjon shouldBe prisinformasjonInTest.copy(
                tilleggsopplysninger = longString.take(MAX_LENGTH_TILLEGGSOPPLYSNINGER),
            )
        }
    }
}
