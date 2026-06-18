@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.enkeltplass.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.internapi.enkeltplass.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class UpsertPrisinfoTests {
        @Test
        fun `lagrer prisinfo med alle felter`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val insertDbo = PrisinfoDbo(
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
                tilleggsopplysninger = "Standard opplysning",
                ingenkostnaderAarsak = null,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(
                gjennomforingId = deltakerliste.id,
                insertDbo = insertDbo,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(deltakerliste.id)
            result shouldNotBe null

            assertSoftly(result.shouldNotBeNull()) {
                prisinfoJsonSubtype shouldBe ANSKAFFELSE_SUB_TYPE
                anskaffelsePris shouldBe 15000
                tilleggsopplysninger shouldBe "Standard opplysning"
                ingenkostnaderAarsak shouldBe null
            }
        }

        @Test
        fun `lagrer prisinfo med minimum felter satt`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val insertDbo = PrisinfoDbo(
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(
                gjennomforingId = deltakerliste.id,
                insertDbo = insertDbo,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(deltakerliste.id)
            assertSoftly(result.shouldNotBeNull()) {
                prisinfoJsonSubtype shouldBe ANSKAFFELSE_SUB_TYPE
                anskaffelsePris shouldBe null
                tilleggsopplysninger shouldBe null
                ingenkostnaderAarsak shouldBe null
            }
        }

        @Test
        fun `oppdaterer eksisterende prisinfo via ON CONFLICT`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val insertDbo1 = PrisinfoDbo(
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
                tilleggsopplysninger = "Original",
                ingenkostnaderAarsak = null,
            )
            PrisinfoRepository.upsertPrisinfo(
                gjennomforingId = deltakerliste.id,
                insertDbo = insertDbo1,
            )

            val insertDbo2 = PrisinfoDbo(
                prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
                anskaffelsePris = null,
                tilleggsopplysninger = "Oppdatert",
                ingenkostnaderAarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(
                gjennomforingId = deltakerliste.id,
                insertDbo = insertDbo2,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(deltakerliste.id)
            assertSoftly(result.shouldNotBeNull()) {
                prisinfoJsonSubtype shouldBe INGENKOSTNADER_SUB_TYPE
                anskaffelsePris shouldBe null
                tilleggsopplysninger shouldBe "Oppdatert"
                ingenkostnaderAarsak shouldBe PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT
            }
        }
    }

    @Nested
    inner class HentPrisinfoTests {
        @Test
        fun `returnerer null når prisinfo ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = PrisinfoRepository.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe null
        }

        @Test
        fun `henter kun for angitt deltakerliste`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            TestRepository.insert(deltakerliste1)

            val deltakerliste2 = lagDeltakerliste()
            TestRepository.insert(deltakerliste2)

            val insertDbo = PrisinfoDbo(
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
                tilleggsopplysninger = "Opplysning",
                ingenkostnaderAarsak = null,
            )
            PrisinfoRepository.upsertPrisinfo(
                gjennomforingId = deltakerliste1.id,
                insertDbo = insertDbo,
            )

            // Act
            val result = PrisinfoRepository.hentPrisinfo(deltakerliste2.id)

            // Assert
            result shouldBe null
        }
    }
}
