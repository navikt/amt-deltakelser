package no.nav.amt.deltaker.bff.veileder.api.request

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.veileder.api.utils.MAX_BEGRUNNELSE_LENGDE
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EndrePrisinfoRequestTest {
    @Nested
    inner class ValiderTest {
        @Test
        fun `valider - gyldig begrunnelse - skal ikke kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = "En gyldig begrunnelse")

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - tom begrunnelse - skal kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = "")

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            // Assert
            exception.message shouldContain "Begrunnelse kan ikke være tom"
        }

        @Test
        fun `valider - begrunnelse lengre enn maks - skal kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = "a".repeat(MAX_BEGRUNNELSE_LENGDE + 1))

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            // Assert
            exception.message shouldContain "Begrunnelse kan ikke være lengre enn $MAX_BEGRUNNELSE_LENGDE"
        }

        @Test
        fun `valider - begrunnelse med maks lengde - skal ikke kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = "a".repeat(MAX_BEGRUNNELSE_LENGDE))

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }
    }

    companion object {
        private fun lagRequest(
            begrunnelse: String = "begrunnelse",
            prisinformasjon: PrisinformasjonDto = PrisinformasjonDto.IngenKostnader(
                aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            ),
        ) = EndrePrisinfoRequest(
            prisinformasjon = prisinformasjon,
            begrunnelse = begrunnelse,
        )
    }
}
