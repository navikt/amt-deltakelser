package no.nav.amt.lib.models.deltakerliste

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PrisinformasjonTest {
    @Nested
    inner class AnskaffelseTest {
        @Test
        fun `skal returnere samme instans ved sanitize()`() {
            // Arrange
            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 1000)

            // Act
            val sanitized = anskaffelse.sanitize()

            // Assert
            sanitized shouldBe anskaffelse
        }

        @Test
        fun `skal returnere samme instans med null pris ved sanitize()`() {
            // Arrange
            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 0)

            // Act
            val sanitized = anskaffelse.sanitize()

            // Assert
            sanitized shouldBe anskaffelse
        }
    }

    @Nested
    inner class TilskuddTest {
        @Test
        fun `skal returnere samme instans med null tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = null,
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd
        }

        @Test
        fun `skal returnere samme instans med ren tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "Ingen ekstra mellomrom",
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd
        }

        @Test
        fun `skal trimme whitespace fra tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "  Tekst med mellomrom  ",
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd.copy(tilleggsopplysninger = "Tekst med mellomrom")
        }

        @Test
        fun `skal begrense tilleggsopplysninger til 600 tegn ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "A".repeat(700),
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd.copy(tilleggsopplysninger = "A".repeat(600))
        }

        @Test
        fun `skal trimme og begrense tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "  ${"B".repeat(700)}  ",
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd.copy(tilleggsopplysninger = "B".repeat(600))
        }

        @Test
        fun `skal bevare flere tilskuddstyper ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 2000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 500,
                ),
                tilleggsopplysninger = "Ekstra info",
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd
        }

        @Test
        fun `skal ikke trunkere med nøyaktig 600 tegn ved sanitize()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000),
                tilleggsopplysninger = "C".repeat(600),
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd
        }
    }

    @Nested
    inner class IngenKostnadenTest {
        @Test
        fun `skal returnere samme instans med null tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader
        }

        @Test
        fun `skal returnere samme instans med ren tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "Ren tekst",
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader
        }

        @Test
        fun `skal trimme whitespace fra tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  Tekst med mellomrom  ",
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader.copy(tilleggsopplysninger = "Tekst med mellomrom")
        }

        @Test
        fun `skal begrense tilleggsopplysninger til 600 tegn ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "D".repeat(700),
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader.copy(tilleggsopplysninger = "D".repeat(600))
        }

        @Test
        fun `skal trimme og begrense tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  ${"E".repeat(700)}  ",
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader.copy(tilleggsopplysninger = "E".repeat(600))
        }

        @Test
        fun `skal ikke trunkere med nøyaktig 600 tegn ved sanitize()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "F".repeat(600),
            )

            // Act
            val sanitized = ingenKostnader.sanitize()

            // Assert
            sanitized shouldBe ingenKostnader
        }

        @Test
        fun `skal fungere korrekt med begge aarsakstyper ved sanitize()`() {
            // Arrange - test med OPPLAERINGEN_ER_EGENFINANSIERT
            val ingenKostnader1 = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "  Tekst  ",
            )

            // Act
            val sanitized1 = ingenKostnader1.sanitize()

            // Assert
            sanitized1 shouldBe ingenKostnader1.copy(tilleggsopplysninger = "Tekst")

            // Arrange - test med OPPLAERINGEN_ER_KOSTNADSFRI
            val ingenKostnader2 = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "  Annen tekst  ",
            )

            // Act
            val sanitized2 = ingenKostnader2.sanitize()

            // Assert
            sanitized2 shouldBe ingenKostnader2.copy(tilleggsopplysninger = "Annen tekst")
        }
    }

    @Nested
    inner class AnskaffelseValidateTest {
        @Test
        fun `skal returnere tom liste når pris er positiv ved validate()`() {
            // Arrange
            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 1000)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere feil når pris er 0 ved validate()`() {
            // Arrange
            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 0)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe listOf(Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG)
        }

        @Test
        fun `skal returnere feil når pris er negativ ved validate()`() {
            // Arrange
            val anskaffelse = Prisinformasjon.Anskaffelse(pris = -500)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe listOf(Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG)
        }
    }

    @Nested
    inner class TilskuddValidateTest {
        @Test
        fun `skal returnere feil naar tilskudd mangler ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = emptyMap(),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe Prisinformasjon.TILSKUDD_REQUIRED_MSG
        }

        @Test
        fun `skal returnere tom liste når alle tilskudd er positive ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 2000,
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere feil når ett tilskudd er null ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 0,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 2000,
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe "${Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER"
        }

        @Test
        fun `skal returnere feil når ett tilskudd er negativt ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to -500,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 2000,
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe "${Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER"
        }

        @Test
        fun `skal returnere flere feil når flere tilskudd er ugyldige ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 0,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to -1000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 500,
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 2
            errors.shouldContain("${Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER")
            errors.shouldContain("${Prisinformasjon.POSITIV_PRIS_REQUIRED_MSG}. STUDIEREISE")
        }

        @Test
        fun `skal validere uavhengig av tilleggsopplysninger ved validate()`() {
            // Arrange
            val tilskudd = Prisinformasjon.Tilskudd(
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                ),
                tilleggsopplysninger = "Lang tekst med mange tegn".repeat(100),
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors shouldBe emptyList()
        }
    }

    @Nested
    inner class IngenKostnadenValidateTest {
        @Test
        fun `skal returnere tom liste for IngenKostnadsfri ved validate()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )

            // Act
            val errors = ingenKostnader.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere tom liste for IngenKostnaderEgenfinansiert ved validate()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = null,
            )

            // Act
            val errors = ingenKostnader.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere tom liste med tilleggsopplysninger ved validate()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "Noen tilleggsopplysninger",
            )

            // Act
            val errors = ingenKostnader.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere tom liste med lang tekst ved validate()`() {
            // Arrange
            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "A".repeat(1000),
            )

            // Act
            val errors = ingenKostnader.validate()

            // Assert
            errors shouldBe emptyList()
        }
    }
}
