package no.nav.amt.deltaker.bff.veileder.api.request

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.veileder.api.utils.MAX_BEGRUNNELSE_LENGDE
import no.nav.amt.lib.models.deltaker.DeltakerStatus
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
        fun `valider - null begrunnelse - skal kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = null)

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            // Assert
            exception.message shouldContain "Begrunnelse kan ikke være tom"
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
        fun `valider - blank begrunnelse - skal kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel()
            val request = lagRequest(begrunnelse = "  ")

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

    @Nested
    inner class ValiderPrisinfoTypeKanEndres {
        @Test
        fun `endrer type etter at okonomi er godkjent - status DELTAR - skal kaste exception`() {
            // Arrange
            val deltaker = lagDeltakerMedGjeldendePrisinfo(
                gjeldendePrisinformasjon = anskaffelse(),
                status = DeltakerStatus.Type.DELTAR,
            )
            val request = lagRequest(prisinformasjon = tilskudd())

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            // Assert
            exception.message shouldContain "Kan ikke endre type prisinformasjon"
        }

        @Test
        fun `endrer type - status UTKAST_TIL_PAMELDING - skal ikke kaste exception`() {
            // Arrange
            val deltaker = lagDeltakerMedGjeldendePrisinfo(
                gjeldendePrisinformasjon = anskaffelse(),
                status = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
            )
            val request = lagRequest(prisinformasjon = tilskudd())

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `endrer type - status SOKT_INN - skal ikke kaste exception`() {
            // Arrange
            val deltaker = lagDeltakerMedGjeldendePrisinfo(
                gjeldendePrisinformasjon = anskaffelse(),
                status = DeltakerStatus.Type.SOKT_INN,
            )
            val request = lagRequest(prisinformasjon = tilskudd())

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `samme type - kun verdiendring - status DELTAR - skal ikke kaste exception`() {
            // Arrange
            val deltaker = lagDeltakerMedGjeldendePrisinfo(
                gjeldendePrisinformasjon = anskaffelse(pris = 10000),
                status = DeltakerStatus.Type.DELTAR,
            )
            val request = lagRequest(prisinformasjon = anskaffelse(pris = 20000))

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `ingen gjeldende prisinfo - status DELTAR - skal ikke kaste exception`() {
            // Arrange
            val deltaker = lagDeltakerMedGjeldendePrisinfo(
                gjeldendePrisinformasjon = null,
                status = DeltakerStatus.Type.DELTAR,
            )
            val request = lagRequest(prisinformasjon = tilskudd())

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }
    }

    companion object {
        private fun anskaffelse(pris: Int = 15000) = PrisinformasjonDto.Anskaffelse(pris = pris)

        private fun tilskudd() = PrisinformasjonDto.Tilskudd(
            tilskudd = listOf(
                PrisinformasjonDto.Tilskudd.TilskuddInfo(
                    type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                    pris = 8000,
                ),
            ),
            tilleggsopplysninger = null,
        )

        private fun lagDeltakerMedGjeldendePrisinfo(
            gjeldendePrisinformasjon: PrisinformasjonDto?,
            status: DeltakerStatus.Type,
        ) = TestData.lagDeltaker(
            gjennomforing = TestData.lagGjennomforingModel().copy(prisinformasjon = gjeldendePrisinformasjon),
            status = TestData.lagDeltakerStatus(status),
        )

        private fun lagRequest(
            begrunnelse: String? = "begrunnelse",
            prisinformasjon: PrisinformasjonDto = PrisinformasjonDto.Anskaffelse(pris = 10000),
        ) = EndrePrisinfoRequest(
            prisinformasjon = prisinformasjon,
            begrunnelse = begrunnelse,
        )
    }
}
