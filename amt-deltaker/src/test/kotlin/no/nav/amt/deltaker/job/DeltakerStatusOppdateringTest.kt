package no.nav.amt.deltaker.job

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment.Companion.DELTAKER_EKSTERN_V1_TOPIC
import no.nav.amt.deltaker.Environment.Companion.DELTAKER_V1_TOPIC
import no.nav.amt.deltaker.Environment.Companion.DELTAKER_V2_TOPIC
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerStatusOppdateringTest : IntegrationTestWithDbBase() {
    private val sistEndretAvNavEnhet = lagNavEnhet()
    private val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert - setter status DELTAR`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.DELTAR
            status.aarsak shouldBe null
            sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert men komet er ikke master - setter status til DELTAR`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
            kilde = Kilde.ARENA,
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns false
        every { unleashToggle.skalLeseArenaDataForTiltakstype(any<Tiltakskode>()) } returns true

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, ikke kurs - setter status HAR_SLUTTET`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().plusMonths(3),
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak shouldBe null
            sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, ikke kurs, har fremtidig status - bruker fremtidig status HAR_SLUTTET`() =
        runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusWeeks(1),
                sluttdato = LocalDate.now().minusDays(2),
                deltakerliste = lagDeltakerliste(
                    oppstart = Oppstartstype.LOPENDE,
                    sluttDato = LocalDate.now().plusMonths(3),
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(deltaker, vedtak)

            val fremtidigStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                gyldigFra = LocalDateTime.now().minusMinutes(1),
                gyldigTil = null,
            )
            DeltakerStatusRepository.lagreStatus(deltaker.id, fremtidigStatus)

            // Act
            deltakerService.oppdaterDeltakerStatuser()

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
                sluttdato shouldBe deltaker.sluttdato
            }
        }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, kurs - setter status FULLFORT`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.FELLES,
                sluttDato = LocalDate.now().minusDays(2),
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.FULLFORT
            sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert og tidligere enn kursets sluttdato - setter status FULLFORT`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.FELLES,
                sluttDato = LocalDate.now().plusDays(2),
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.FULLFORT
            sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avsluttet, status DELTAR - setter status HAR_SLUTTET, oppdatert sluttdato`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVSLUTTET,
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak shouldBe null
            sluttdato shouldBe deltaker.deltakerliste.sluttDato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avsluttet, status VENTER_PA_OPPSTART - setter status IKKE_AKTUELL`() = runTest {
        // Arrange*
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVSLUTTET,
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            status.aarsak shouldBe null
            sluttdato shouldBe null
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avlyst, status DELTAR - setter status HAR_SLUTTET med sluttarsak`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVLYST,
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            sluttdato shouldBe deltaker.deltakerliste.sluttDato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avbrutt, status VENTER_PA_OPPSTART - setter status IKKE_AKTUELL med sluttarsak`() =
        runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
                deltakerliste = lagDeltakerliste(
                    oppstart = Oppstartstype.LOPENDE,
                    sluttDato = LocalDate.now().minusDays(2),
                    status = GjennomforingStatusType.AVBRUTT,
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(deltaker, vedtak)

            // Act
            deltakerService.oppdaterDeltakerStatuser()

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
                sluttdato shouldBe null
            }
        }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avbrutt, status UTKAST_TIL_PAMELDING - setter status AVBRUTT_UTKAST`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            startdato = null,
            sluttdato = null,
            deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVBRUTT,
            ),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            status.aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            sluttdato shouldBe null
        }

        outboxService.assertProducedHendelse<HendelseType.AvbrytUtkast>(deltaker.id)
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert - publiserer til kafka`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
        )
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
        )
        TestRepository.insert(deltaker, vedtak)

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, DELTAKER_V2_TOPIC)
        outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, DELTAKER_V1_TOPIC)
        outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, DELTAKER_EKSTERN_V1_TOPIC)
    }

    @Test
    fun `oppdaterDeltakerStatuser - feil for en deltaker - oppdaterer de andre`() = runTest {
        // Arrange
        val deltaker1 = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
        )
        val vedtak1 = lagVedtak(
            deltakerId = deltaker1.id,
            deltakerVedVedtak = deltaker1,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
        )
        TestRepository.insert(deltaker1, vedtak1)

        val deltaker2 = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
        )
        val vedtak2 = lagVedtak(
            deltakerId = deltaker2.id,
            deltakerVedVedtak = deltaker2,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
        )
        TestRepository.insert(deltaker2, vedtak2)

        every {
            outboxService.insertRecord(
                key = deltaker1.id,
                value = any(),
                topic = any(),
                suppressOutsideTxWarning = any(),
            )
        } throws RuntimeException("Simulert feil for deltaker1")

        // Act
        deltakerService.oppdaterDeltakerStatuser()

        // Assert
        deltakerRepository
            .get(deltaker2.id)
            .shouldBeSuccess()
            .status.type shouldBe DeltakerStatus.Type.DELTAR

        deltakerRepository
            .get(deltaker1.id)
            .shouldBeSuccess()
            .status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
    }
}
