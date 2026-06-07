package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.kafka.payload.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.kafka.payload.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeEqualTo
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime

class EnkeltplassDeltakerConsumerTest {
    private val unleashToggle = mockk<CommonUnleashToggle>()
    private val navBrukerService = mockk<NavBrukerService>()
    private val deltakerKafkaPayloadBuilder = mockk<DeltakerKafkaPayloadBuilder>()
    private val deltakerProducer = mockk<DeltakerProducer>()
    private val deltakerEksternV1Producer = mockk<DeltakerEksternV1Producer>()
    private val deltakerRepository = DeltakerRepository()
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val tiltakRepository = TiltakRepository()
    private val deltakerProducerService = DeltakerProducerService(
        deltakerKafkaPayloadBuilder = deltakerKafkaPayloadBuilder,
        deltakerProducer = deltakerProducer,
        deltakerV1Producer = mockk(),
        deltakerEksternV1Producer = deltakerEksternV1Producer,
        unleashToggle = unleashToggle,
    )
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerProducerService = deltakerProducerService,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerEndringRepository = mockk(),
        deltakerEndringService = mockk(),
        navAnsattService = mockk(),
        vedtakRepository = mockk(),
        vedtakService = mockk(),
        distribuerEndringService = mockk(),
        endringFraArrangorRepository = mockk(),
        deltakerHistorikkService = mockk(),
        endringFraTiltakskoordinatorRepository = mockk(),
        forslagRepository = mockk(),
        unleashToggle = mockk(),
    )

    private val consumer = EnkeltplassDeltakerConsumer(
        deltakerRepository,
        deltakerService,
        deltakerlisteRepository,
        navBrukerService,
        importertFraArenaRepository,
        unleashToggle,
        deltakerProducerService,
    )

    @BeforeEach
    fun setup() {
        every { unleashToggle.skalProdusereTilDeltakerEksternTopic() } returns true
        every { deltakerProducer.produce(any()) } just Runs
        every { deltakerEksternV1Producer.produce(any()) } just Runs
        every { deltakerKafkaPayloadBuilder.buildDeltakerV1Record(any()) } returns mockk()
        every { deltakerKafkaPayloadBuilder.buildDeltakerEksternV1Record(any()) } returns mockk()
        every { deltakerKafkaPayloadBuilder.buildDeltakerV2Record(any()) } returns mockk()
    }

    private fun mockArenaFlagsAndNavBruker(deltaker: Deltaker) {
        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)
    }

    @Test
    fun `consumeDeltaker - skalLeseArenaDataForTiltakstype=false - lagrer ikke enkeltplasser og produserer ikke til deltaker-v2 topic`() {
        // Arrange
        val deltakerListe = lagEnkeltplassDeltakerliste()
        TestRepository.insert(deltakerListe)

        val deltaker = lagArenaDeltaker(deltakerliste = deltakerListe)

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        // Act
        runTest {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        // Assert - når unleash-toggle er false returnerer consumer tidlig og ingenting skal skje
        // Sjekk at deltaker IKKE ble lagret
        deltakerRepository.get(deltaker.id).shouldBeFailure()

        // Verifiser at ingen service-kall ble gjort
        coVerify(exactly = 0) { navBrukerService.get(any()) }
    }

    @Test
    fun `consumeDeltaker - gjennomforing er allerede lagret - lagrer enkeltplasser og produserer til deltaker-v2`() {
        // Arrange
        val deltakerListe = lagEnkeltplassDeltakerliste()
        TestRepository.insert(deltakerListe)

        val deltaker = lagArenaDeltaker(deltakerliste = deltakerListe)
        TestRepository.insert(deltaker.navBruker)

        val importertFraArena = lagImportertFraArenaHistorikk(deltaker)

        mockArenaFlagsAndNavBruker(deltaker)

        // Act
        runTest {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        // Assert - at consumer faktisk behandlet deltakeren
        coVerify(exactly = 1) { navBrukerService.get(deltaker.navBruker.personident) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
        assertSoftly(deltakerFromDb) {
            id shouldBe deltaker.id
            deltakerliste.id shouldBe deltaker.deltakerliste.id
            status.type shouldBe deltaker.status.type
            bakgrunnsinformasjon.shouldBeNull() // kommer fra payload
        }

        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        assertSoftly(importertFraArenaFromDb.shouldNotBeNull()) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    @Test
    fun `consumeDeltaker - gjennomforing eksisterer ikke i db - kaster exception`() {
        // Arrange
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
            oppmoteSted = null,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val deltaker = lagArenaDeltaker(deltakerliste = deltakerListe)
        tiltakRepository.upsert(deltakerListe.tiltakstype)
        TestRepository.insert(deltaker.navBruker)

        // Act & Assert
        runTest {
            val thrown = shouldThrow<IllegalStateException> {
                consumer.consumeDeltaker(toPayload(deltaker))
            }

            thrown.message shouldBe "Deltakerliste ${deltakerListe.id} ikke mottatt fra Mulighetsrommet ennå"
        }
    }

    @Test
    fun `consumeDeltaker - eksisterende deltaker - status endres fra DELTAR til FULLFORT - oppdaterer og produserer`() {
        // Arrange
        val deltakerListe = lagEnkeltplassDeltakerliste()
        val deltaker = lagArenaDeltaker(deltakerliste = deltakerListe)

        val nyStatus = lagDeltakerStatus(
            statusType = DeltakerStatus.Type.FULLFORT,
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val deltakerMedEndringer = deltaker.copy(
            status = nyStatus,
            sluttdato = LocalDate.now().minusDays(1),
        )

        TestRepository.insert(deltaker)
        tiltakRepository.upsert(deltakerListe.tiltakstype)

        val importertFraArena = lagImportertFraArenaHistorikk(deltakerMedEndringer)

        mockArenaFlagsAndNavBruker(deltaker)

        // Act
        runTest {
            consumer.consumeDeltaker(toPayload(deltakerMedEndringer))
        }

        // Assert - at consumer behandlet statusendringen
        coVerify(exactly = 1) { navBrukerService.get(deltaker.navBruker.personident) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()

        val expectedDeltaker = deltakerMedEndringer.copy(
            status = deltakerMedEndringer.status.copy(
                id = deltakerFromDb.status.id,
                opprettet = deltakerFromDb.status.opprettet,
            ),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        assertSoftly(importertFraArenaFromDb.shouldNotBeNull()) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    @Test
    fun `consumeDeltaker - eksisterende deltaker - samme status - andre endringer på deltaker - oppdaterer og produserer`() {
        // Arrange
        val deltakerListe = lagEnkeltplassDeltakerliste()
        val deltaker = lagArenaDeltaker(deltakerliste = deltakerListe)

        val deltakerMedEndringer = deltaker.copy(
            sluttdato = LocalDate.now().plusDays(1),
        )

        TestRepository.insert(deltaker)
        tiltakRepository.upsert(deltakerListe.tiltakstype)
        val importertFraArena = lagImportertFraArenaHistorikk(deltakerMedEndringer)

        mockArenaFlagsAndNavBruker(deltaker)

        // Act
        runTest {
            consumer.consumeDeltaker(toPayload(deltakerMedEndringer))
        }

        // Assert - at consumer behandlet endringene
        coVerify(exactly = 1) { navBrukerService.get(deltaker.navBruker.personident) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()

        val expectedDeltaker = deltakerMedEndringer.copy(
            status = deltakerMedEndringer.status.copy(
                id = deltakerFromDb.status.id,
                opprettet = deltakerFromDb.status.opprettet,
            ),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        assertSoftly(importertFraArenaFromDb.shouldNotBeNull()) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun lagEnkeltplassDeltakerliste() = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        private fun lagArenaDeltaker(
            deltakerliste: Deltakerliste,
            statusOpprettet: LocalDateTime = LocalDateTime.now().minusWeeks(1),
            sistEndret: LocalDateTime = LocalDateTime.now().minusDays(1),
        ) = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )

        private fun lagImportertFraArenaHistorikk(deltaker: Deltaker) = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        private fun toPayload(
            deltaker: Deltaker,
            registrertDato: LocalDateTime = deltaker.opprettet,
            statusEndretDato: LocalDateTime = deltaker.status.gyldigFra,
            innsokBegrunnelse: String? = null,
        ) = EnkeltplassDeltakerPayload(
            id = deltaker.id,
            gjennomforingId = deltaker.deltakerliste.id,
            personIdent = deltaker.navBruker.personident,
            startDato = deltaker.startdato,
            sluttDato = deltaker.sluttdato,
            status = deltaker.status.type,
            statusAarsak = deltaker.status.aarsak?.type,
            dagerPerUke = deltaker.dagerPerUke,
            prosentDeltid = deltaker.deltakelsesprosent,
            registrertDato = registrertDato,
            statusEndretDato = statusEndretDato,
            innsokBegrunnelse = innsokBegrunnelse,
        )
    }
}
