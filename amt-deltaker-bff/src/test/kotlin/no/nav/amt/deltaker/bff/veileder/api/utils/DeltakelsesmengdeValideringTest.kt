package no.nav.amt.deltaker.bff.veileder.api.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerModel
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakelsesmengdeValideringTest {
    @Nested
    inner class ValiderDeltakelsesmengde {
        @Test
        fun `samme mengde uten prosent avvises som uendret`() {
            // Arrange
            val gyldigFra = LocalDate.of(2026, 12, 1)
            val deltaker = lagDeltakerModel().copy(
                deltakelsesmengder = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, 3F, gyldigFra),
                ),
            )

            // Act
            val feil = shouldThrow<IllegalArgumentException> {
                validerDeltakelsesmengde(null, 3, gyldigFra, deltaker)
            }

            // Assert
            feil.message shouldBe "Deltakelsesmengdeendringen er ikke en reell endring"
        }

        @Test
        fun `uendret tom mengde avvises`() {
            // Arrange
            val gyldigFra = LocalDate.of(2026, 12, 1)
            val deltaker = lagDeltakerModel().copy(
                deltakelsesmengder = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, null, gyldigFra),
                ),
            )

            // Act
            val feil = shouldThrow<IllegalArgumentException> {
                validerDeltakelsesmengde(null, null, gyldigFra, deltaker)
            }

            // Assert
            feil.message shouldBe "Deltakelsesmengdeendringen er ikke en reell endring"
        }

        @Test
        fun `registrerte dager er en endring fra tom mengde`() {
            // Arrange
            val gyldigFra = LocalDate.of(2026, 12, 1)
            val deltaker = lagDeltakerModel().copy(
                deltakelsesmengder = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, null, gyldigFra),
                ),
            )

            // Act
            val resultat = runCatching {
                validerDeltakelsesmengde(null, 3, gyldigFra, deltaker)
            }

            // Assert
            resultat.shouldBeSuccess()
        }

        @Test
        fun `eksplisitt hundre prosent skiller seg fra manglende prosent`() {
            // Arrange
            val gyldigFra = LocalDate.of(2026, 12, 1)
            val deltaker = lagDeltakerModel().copy(
                deltakelsesmengder = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, 3F, gyldigFra),
                ),
            )

            // Act
            val resultat = runCatching {
                validerDeltakelsesmengde(100, 3, gyldigFra, deltaker)
            }

            // Assert
            resultat.shouldBeSuccess()
        }

        @Test
        fun `tom mengde kan fortsatt flyttes til tidligere dato`() {
            // Arrange
            val gyldigFra = LocalDate.of(2026, 12, 1)
            val deltaker = lagDeltakerModel().copy(
                deltakelsesmengder = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, null, gyldigFra),
                ),
            )

            // Act
            val resultat = runCatching {
                validerDeltakelsesmengde(null, null, gyldigFra.minusDays(1), deltaker)
            }

            // Assert
            resultat.shouldBeSuccess()
        }

        @Test
        fun `manglende sammenligningsgrunnlag tillater fortsatt endring`() {
            // Arrange
            val deltaker = lagDeltakerModel().copy(deltakelsesmengder = null)

            // Act
            val resultat = runCatching {
                validerDeltakelsesmengde(null, 3, LocalDate.of(2026, 12, 1), deltaker)
            }

            // Assert
            resultat.shouldBeSuccess()
        }
    }
}
