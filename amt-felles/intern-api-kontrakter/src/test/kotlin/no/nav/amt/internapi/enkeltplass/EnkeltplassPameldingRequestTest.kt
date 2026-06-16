@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.internapi.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import org.junit.jupiter.api.Test

class EnkeltplassPameldingRequestTest {
    @Test
    fun sanitized() {
        // Arrange
        val longString = "a".repeat(1000)

        // Act
        val sanitizedRequest = EnkeltplassPameldingRequest(
            beskrivelse = longString,
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
            arrangorUnderenhet = longString,
            startdato = null,
            sluttdato = null,
        ).sanitized()

        // Assert
        assertSoftly(sanitizedRequest) {
            beskrivelse shouldBe "a".repeat(MAX_LENGTH_BESKRIVELSE)
            // TODO prisinformasjon shouldBe "a".repeat(MAX_LENGTH_PRISINFORMASJON)
            arrangorUnderenhet shouldBe "a".repeat(MAX_LENGTH_ARRANGOR_UNDERENHET)
        }
    }
}
