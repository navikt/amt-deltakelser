package no.nav.amt.internapi.enkeltplass

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PrisinformasjonDtoTest {
    @Nested
    inner class AnskaffelseTest {
        @Test
        fun `skal returnere samme instans ved sanitize()`() {
            // Arrange
            val anskaffelse = no.nav.amt.lib.models.deltaker.PrisinformasjonDto
                .Anskaffelse(pris = 1000)

            // Act
            val sanitized = anskaffelse.sanitize()

            // Assert
            sanitized shouldBe anskaffelse
        }

        @Test
        fun `skal returnere samme instans med null pris ved sanitize()`() {
            // Arrange
            val anskaffelse = no.nav.amt.lib.models.deltaker.PrisinformasjonDto
                .Anskaffelse(pris = 0)

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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                        pris = 2000,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
                        pris = 500,
                    ),
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = tilskuddInTest,
                tilleggsopplysninger = "C".repeat(600),
            )

            // Act
            val sanitized = tilskudd.sanitize()

            // Assert
            sanitized shouldBe tilskudd
        }
    }

    @Nested
    inner class IngenKostnaderTest {
        @Test
        fun `skal returnere samme instans med null tilleggsopplysninger ved sanitize()`() {
            // Arrange
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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
            val ingenKostnader1 = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "  Tekst  ",
            )

            // Act
            val sanitized1 = ingenKostnader1.sanitize()

            // Assert
            sanitized1 shouldBe ingenKostnader1.copy(tilleggsopplysninger = "Tekst")

            // Arrange - test med OPPLAERINGEN_ER_KOSTNADSFRI
            val ingenKostnader2 = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val anskaffelse = no.nav.amt.lib.models.deltaker.PrisinformasjonDto
                .Anskaffelse(pris = 1000)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe emptyList()
        }

        @Test
        fun `skal returnere feil når pris er 0 ved validate()`() {
            // Arrange
            val anskaffelse = no.nav.amt.lib.models.deltaker.PrisinformasjonDto
                .Anskaffelse(pris = 0)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe listOf(PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG)
        }

        @Test
        fun `skal returnere feil når pris er negativ ved validate()`() {
            // Arrange
            val anskaffelse = no.nav.amt.lib.models.deltaker.PrisinformasjonDto
                .Anskaffelse(pris = -500)

            // Act
            val errors = anskaffelse.validate()

            // Assert
            errors shouldBe listOf(PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG)
        }
    }

    @Nested
    inner class TilskuddValidateTest {
        @Test
        fun `skal returnere feil naar tilskudd mangler ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = emptyList(),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe PrisinformasjonDto.TILSKUDD_REQUIRED_MSG
        }

        @Test
        fun `skal returnere feil naar duplikate tilskudd ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 2000,
                    ),
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldStartWith "Tilskudd kan ikke inneholde flere elementer med samme type"
        }

        @Test
        fun `skal returnere tom liste når alle tilskudd er positive ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                        pris = 2000,
                    ),
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
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 0,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                        pris = 2000,
                    ),
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe "${PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER"
        }

        @Test
        fun `skal returnere feil når ett tilskudd er negativt ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = -500,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                        pris = 2000,
                    ),
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 1
            errors.first() shouldBe "${PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER"
        }

        @Test
        fun `skal returnere flere feil når flere tilskudd er ugyldige ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 0,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                        pris = -1000,
                    ),
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
                        pris = 500,
                    ),
                ),
                tilleggsopplysninger = null,
            )

            // Act
            val errors = tilskudd.validate()

            // Assert
            errors.size shouldBe 2
            errors.shouldContain("${PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG}. SKOLEPENGER")
            errors.shouldContain("${PrisinformasjonDto.POSITIV_PRIS_REQUIRED_MSG}. STUDIEREISE")
        }

        @Test
        fun `skal validere uavhengig av tilleggsopplysninger ved validate()`() {
            // Arrange
            val tilskudd = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd(
                tilskudd = listOf(
                    no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
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
    inner class IngenKostnaderValidateTest {
        @Test
        fun `skal returnere tom liste for IngenKostnadsfri ved validate()`() {
            // Arrange
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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
            val ingenKostnader = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader(
                aarsak = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "A".repeat(1000),
            )

            // Act
            val errors = ingenKostnader.validate()

            // Assert
            errors shouldBe emptyList()
        }
    }

    companion object {
        private val tilskuddInTest = listOf(
            no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo(
                type = no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                pris = 5000,
            ),
        )
    }
}
