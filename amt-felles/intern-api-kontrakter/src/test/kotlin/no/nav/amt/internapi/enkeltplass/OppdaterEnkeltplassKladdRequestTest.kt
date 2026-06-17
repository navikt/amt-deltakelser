package no.nav.amt.internapi.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon.Companion.MAX_LENGTH_TILLEGGSOPPLYSNINGER
import org.junit.jupiter.api.Test

class OppdaterEnkeltplassKladdRequestTest {
    @Test
    fun sanitized() {
        // Arrange
        val longString = "a".repeat(1000)
        val prisinformasjonInTest = Prisinformasjon.IngenKostnader(
            aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = longString,
        )

        // Act
        val sanitizedRequest = OppdaterEnkeltplassKladdRequest(
            beskrivelse = longString,
            arrangorUnderenhet = longString,
            startdato = null,
            sluttdato = null,
            prisinformasjon = prisinformasjonInTest,
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
