@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class PrisinfoRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()
    }

    @Nested
    inner class UpsertPendingTotrinnskontrollPrisinfoTests {
        @Test
        fun `lagrer prisinfo med alle felter`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val insertDbo = PrisinfoDbo(
                id = UUID.randomUUID(),
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
                tilleggsopplysninger = "Standard opplysning",
                ingenkostnaderAarsak = null,
            )

            // Act
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(insertDbo)

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
            )

            result shouldNotBe null

            assertSoftly(result.shouldNotBeNull()) {
                id shouldBe insertDbo.id
                gjennomforingId shouldBe insertDbo.gjennomforingId
                okonomiGodkjent shouldBe false
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
                id = UUID.randomUUID(),
                gjennomforingId = deltakerliste.id,
                okonomiGodkjent = false,
                prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            )

            // Act
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(insertDbo = insertDbo)

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                okonomiGodkjent = false,
            )

            assertSoftly(result.shouldNotBeNull()) {
                prisinfoJsonSubtype shouldBe INGENKOSTNADER_SUB_TYPE
                anskaffelsePris shouldBe null
                tilleggsopplysninger shouldBe null
                ingenkostnaderAarsak shouldBe null
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
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                okonomiGodkjent = false,
            )

            // Assert
            result shouldBe null
        }
    }
}
