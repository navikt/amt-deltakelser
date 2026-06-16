package no.nav.amt.internapi.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import org.junit.jupiter.api.Test

class OppdaterEnkeltplassKladdRequestTest {
    @Test
    fun sanitized() {
        // Arrange
        val longString = "a".repeat(1000)

        // Act
        val sanitizedRequest = OppdaterEnkeltplassKladdRequest(
            beskrivelse = longString,
            arrangorUnderenhet = longString,
            startdato = null,
            sluttdato = null,
            prisinformasjon = Prisinformasjon.Anskaffelse(pris = 1000000),
        ).sanitized()

        // Assert
        assertSoftly(sanitizedRequest) {
            beskrivelse shouldBe "a".repeat(MAX_LENGTH_BESKRIVELSE)
            // TODO prisinformasjon shouldBe "a".repeat(MAX_LENGTH_PRISINFORMASJON)
            arrangorUnderenhet shouldBe "a".repeat(MAX_LENGTH_ARRANGOR_UNDERENHET)
        }
    }
}
