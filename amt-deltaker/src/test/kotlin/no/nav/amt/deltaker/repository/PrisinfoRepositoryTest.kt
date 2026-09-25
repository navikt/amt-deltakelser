@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.PrisinfoUpsertDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class PrisinfoRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()
    }

    @Nested
    inner class UpsertPrisinfoTests {
        @Test
        fun `lagrer prisinfo med alle felter`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
                tilleggsopplysninger = "Standard opplysning",
                ingenkostnaderAarsak = null,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(upsertDbo)

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = upsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            result shouldNotBe null

            assertSoftly(result.shouldNotBeNull()) {
                gjennomforingId shouldBe gjennomforingInTest.id
                status shouldBe PrisinfoDbo.PrisinfoStatus.KLADD_UTKAST
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

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(upsertDbo = upsertDbo)

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = upsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
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
    inner class HentPrisinfoStatusTests {
        @Test
        fun `returnerer status når prisinfo finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = upsertDbo.id,
            )

            // Assert
            result shouldBe PrisinfoDbo.PrisinfoStatus.KLADD_UTKAST
        }

        @Test
        fun `returnerer oppdatert status etter oppdaterStatus`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)
            PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(upsertDbo.id, PrisinfoDbo.PrisinfoStatus.RETURNERT)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = upsertDbo.id,
            )

            // Assert
            result shouldBe PrisinfoDbo.PrisinfoStatus.RETURNERT
        }

        @Test
        fun `returnerer null når prisinfoId ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = UUID.randomUUID(),
            )

            // Assert
            result shouldBe null
        }

        @Test
        fun `returnerer null når gjennomforingId ikke stemmer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = UUID.randomUUID(),
                prisinformasjonId = upsertDbo.id,
            )

            // Assert
            result shouldBe null
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
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            result shouldBe null
        }
    }

    @Nested
    inner class HentPrisinfoListeForHistorikkTests {
        private val navEnhet = lagNavEnhet()
        val deltakerliste = lagDeltakerliste()
        val navAnsatt = lagNavAnsatt()
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun beforeEach() {
            NavEnhetRepository().upsert(navEnhet)
            TestRepository.insert(navAnsatt)
        }

        @Test
        fun `returnerer tom liste når deltaker ikke har prisinfo`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer tom liste når prisinfo ikke er godkjent`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer hvem som godkjente prisinfo`() {
            // Arrange
            val sistEndret = LocalDateTime.now().minusDays(1)
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                sistEndret = sistEndret,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 20000,
                tilleggsopplysninger = "Opplysning",
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)
            PrisinfoRepository.settGodkjent(upsertDbo.id, godkjentAv = navAnsatt.id, godkjentAvEnhet = navEnhet.id)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
            assertSoftly(result.first()) {
                godkjentAvNavAnsattId shouldBe navAnsatt.id
                godkjentAvNavEnhetId shouldBe navEnhet.id
            }
        }

        @Test
        fun `returnerer bare godkjent prisinfo, ikke kladd`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val godkjentPrisinfo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            val kladdPrisinfo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 25000,
            )
            PrisinfoRepository.upsertPrisinfo(godkjentPrisinfo)
            PrisinfoRepository.upsertPrisinfo(kladdPrisinfo)
            PrisinfoRepository.settGodkjent(godkjentPrisinfo.id, godkjentAv = navAnsatt.id, godkjentAvEnhet = navEnhet.id)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert - bare den godkjente returneres
            result shouldHaveSize 1
        }

        @Test
        fun `returnerer tom liste for deltaker uten vedtak`() {
            TestRepository.insert(deltaker)

            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)
            PrisinfoRepository.settGodkjent(upsertDbo.id, godkjentAv = navAnsatt.id, godkjentAvEnhet = navEnhet.id)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer ikke prisinfo fra en annen deltakers deltakerliste`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()

            val deltaker1 = lagDeltaker(
                deltakerliste = deltakerliste1,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste2,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val vedtak1 = lagVedtak(
                deltakerId = deltaker1.id,
                deltakerVedVedtak = deltaker1,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            val vedtak2 = lagVedtak(
                deltakerId = deltaker2.id,
                deltakerVedVedtak = deltaker2,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker1, vedtak1)
            TestRepository.insert(deltaker2, vedtak2)

            // Prisinfo on deltakerliste2 only
            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste2.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 30000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)
            PrisinfoRepository.settGodkjent(upsertDbo.id, godkjentAv = navAnsatt.id, godkjentAvEnhet = navEnhet.id)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker1.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `setter erForsteGodkjenning til true når godkjenning skjedde før vedtaket ble fattet`() {
            // Arrange - vedtaket fattes etter at prisinfo ble godkjent
            insertDeltakerMedVedtak(fattet = LocalDateTime.now().plusMinutes(5))
            godkjennNyPrisinfo()

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
            assertSoftly(result.first()) {
                erForsteGodkjenning shouldBe true
                prisinfo.anskaffelsePris shouldBe 20000
            }
        }

        @Test
        fun `setter erForsteGodkjenning til false når godkjenning skjedde etter at vedtaket ble fattet`() {
            // Arrange - prisendring godkjennes etter at vedtaket er fattet
            insertDeltakerMedVedtak(fattet = LocalDateTime.now().minusMinutes(5))
            godkjennNyPrisinfo()

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
            result.first().erForsteGodkjenning shouldBe false
        }

        @Test
        fun `setter erForsteGodkjenning til false når vedtaket ikke er fattet`() {
            // Arrange
            insertDeltakerMedVedtak(fattet = null)
            godkjennNyPrisinfo()

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
            result.first().erForsteGodkjenning shouldBe false
        }

        private fun insertDeltakerMedVedtak(fattet: LocalDateTime?) {
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                fattet = fattet,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)
        }

        private fun godkjennNyPrisinfo() {
            val upsertDbo = PrisinfoUpsertDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 20000,
            )
            PrisinfoRepository.upsertPrisinfo(upsertDbo)
            PrisinfoRepository.settGodkjent(upsertDbo.id, godkjentAv = navAnsatt.id, godkjentAvEnhet = navEnhet.id)
        }
    }
}
