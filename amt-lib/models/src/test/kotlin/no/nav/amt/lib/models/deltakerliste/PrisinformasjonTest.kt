package no.nav.amt.lib.models.deltakerliste

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PrisinformasjonTest {
    @Nested
    inner class AnskaffelseTest {
        @Test
        fun `skal returnere samme instans ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Anskaffelse(pris = 1000)

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal returnere samme instans med null pris ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Anskaffelse(pris = 0)

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }
    }

    @Nested
    inner class TilskuddTest {
        @Test
        fun `skal returnere samme instans med null tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = null,
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal returnere samme instans med ren tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "Ingen ekstra mellomrom",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal trimme whitespace fra tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "  Tekst med mellomrom  ",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "Tekst med mellomrom")
        }

        @Test
        fun `skal begrense tilleggsopplysninger til 600 tegn ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "A".repeat(700),
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "A".repeat(600))
        }

        @Test
        fun `skal trimme og begrense tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val langTekst = "  ${"B".repeat(700)}  "
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = langTekst,
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "B".repeat(600))
        }

        @Test
        fun `skal bevare flere tilskuddstyper ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 2000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 500,
                ),
                tilleggsopplysninger = "Ekstra info",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal ikke trunkere med nøyaktig 600 tegn ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "C".repeat(600),
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }
    }

    @Nested
    inner class IngenKostnadenTest {
        @Test
        fun `skal returnere samme instans med null tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal returnere samme instans med ren tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "Ren tekst",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal trimme whitespace fra tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  Tekst med mellomrom  ",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "Tekst med mellomrom")
        }

        @Test
        fun `skal begrense tilleggsopplysninger til 600 tegn ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "D".repeat(700),
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "D".repeat(600))
        }

        @Test
        fun `skal trimme og begrense tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  ${"E".repeat(700)}  ",
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original.copy(tilleggsopplysninger = "E".repeat(600))
        }

        @Test
        fun `skal ikke trunkere med nøyaktig 600 tegn ved sanitize()`() {
            // Arrange
            val original = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "F".repeat(600),
            )

            // Act
            val sanitized = original.sanitize()

            // Assert
            sanitized shouldBe original
        }

        @Test
        fun `skal fungere korrekt med begge aarsakstyper ved sanitize()`() {
            // Arrange - test med OPPLAERINGEN_ER_EGENFINANSIERT
            val original1 = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "  Tekst  ",
            )

            // Act
            val sanitized1 = original1.sanitize()

            // Assert
            sanitized1 shouldBe original1.copy(tilleggsopplysninger = "Tekst")

            // Arrange - test med OPPLAERINGEN_ER_KOSTNADSFRI
            val original2 = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  Annen tekst  ",
            )

            // Act
            val sanitized2 = original2.sanitize()

            // Assert
            sanitized2 shouldBe original2.copy(tilleggsopplysninger = "Annen tekst")
        }
    }
}
