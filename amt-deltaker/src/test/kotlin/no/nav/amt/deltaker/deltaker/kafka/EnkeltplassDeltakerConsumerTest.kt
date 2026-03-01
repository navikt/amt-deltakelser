package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeEqualTo
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class EnkeltplassDeltakerConsumerTest {
    private val unleashToggle = mockk<CommonUnleashToggle>()
    private val mulighetsrommetApiClient = mockk<MulighetsrommetApiClient>()
    private val arrangorRepository = ArrangorRepository()
    private val arrangorService = mockk<ArrangorService>()
    private val navBrukerService = mockk<NavBrukerService>()
    private val deltakerKafkaPayloadBuilder = mockk<DeltakerKafkaPayloadBuilder>()
    private val deltakerProducer = mockk<DeltakerProducer>()
    private val deltakerEksternV1Producer = mockk<DeltakerEksternV1Producer>()
    private val deltakerRepository = spyk(DeltakerRepository())
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val tiltakstypeRepository = TiltakstypeRepository()
    private val deltakerProducerService = spyk(
        DeltakerProducerService(
            deltakerKafkaPayloadBuilder = deltakerKafkaPayloadBuilder,
            deltakerProducer = deltakerProducer,
            deltakerV1Producer = mockk(),
            deltakerEksternV1Producer = deltakerEksternV1Producer,
            unleashToggle = unleashToggle,
        ),
    )
    private val deltakerService = spyk(
        DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
            importertFraArenaRepository = importertFraArenaRepository,
            deltakerEndringRepository = mockk(),
            deltakerEndringService = mockk(),
            navAnsattService = mockk(),
            vedtakRepository = mockk(),
            vedtakService = mockk(),
            hendelseService = mockk(),
            endringFraArrangorRepository = mockk(),
            deltakerHistorikkService = mockk(),
            endringFraTiltakskoordinatorRepository = mockk(),
            navEnhetService = mockk(),
            forslagRepository = mockk(),
        ),
    )

    private val consumer = EnkeltplassDeltakerConsumer(
        deltakerRepository,
        deltakerService,
        deltakerlisteRepository,
        navBrukerService,
        importertFraArenaRepository,
        unleashToggle,
        mulighetsrommetApiClient,
        arrangorService,
        tiltakstypeRepository,
        deltakerProducerService,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { unleashToggle.skalProdusereTilDeltakerEksternTopic() } returns true
        every { deltakerProducer.produce(any()) } just Runs
        every { deltakerEksternV1Producer.produce(any()) } just Runs
        every { deltakerEksternV1Producer.produce(any()) } just Runs
        every { deltakerKafkaPayloadBuilder.buildDeltakerV1Record(any()) } returns mockk()
        every { deltakerKafkaPayloadBuilder.buildDeltakerEksternV1Record(any()) } returns mockk()
        every { deltakerKafkaPayloadBuilder.buildDeltakerV2Record(any()) } returns mockk()
    }

    @Test
    fun `consumeDeltaker - skalLeseArenaDataForTiltakstype=false - lagrer ikke enkeltplasser og produserer ikke til deltaker-v2 topic`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )
        TestRepository.insert(deltakerListe)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = sistEndret),
            sistEndret = sistEndret,
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        runTest {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 0) { deltakerService.upsertAndProduceDeltaker(any(), true) }
        verify(exactly = 0) { deltakerProducer.produce(any()) }
    }

    @Test
    fun `consumeDeltaker - gjennomforing er allerede lagret - lagrer enkeltplasser og produserer til deltaker-v2`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )
        TestRepository.insert(deltakerListe)

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        TestRepository.insert(deltaker.navBruker)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runTest {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.transactionalDeltakerUpsert(
                deltaker = match {
                    it.id == deltaker.id &&
                        it.deltakerliste.id == deltaker.deltakerliste.id &&
                        it.status.type == deltaker.status.type &&
                        it.bakgrunnsinformasjon == null // comes from payload
                },
                erDeltakerSluttdatoEndret = any(),
                nesteStatus = any(),
                beforeDeltakerUpsert = any(),
                afterDeltakerUpsert = any(),
            )
        }

        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        assertSoftly(deltakerFromDb) {
            id shouldBe deltaker.id
            deltakerliste.id shouldBe deltaker.deltakerliste.id
            status.type shouldBe deltaker.status.type
            bakgrunnsinformasjon.shouldBeNull() // comes from payload
        }

        assertSoftly(importertFraArenaFromDb) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    @Test
    fun `consumeDeltaker - gjennomforing eksisterer ikke i db - henter gjennomforing synkront fra mulighetsrommet api og lagrer`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
            oppmoteSted = null,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        arrangorRepository.upsert(deltakerListe.arrangor)

        tiltakstypeRepository.upsert(deltakerListe.tiltakstype)
        TestRepository.insert(deltaker.navBruker)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )
        coEvery { arrangorService.hentArrangor(deltakerListe.arrangor.organisasjonsnummer) } returns deltakerListe.arrangor
        coEvery { mulighetsrommetApiClient.hentGjennomforingV2(deltakerListe.id) } returns deltakerListe.toV2Response()
        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runTest {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.transactionalDeltakerUpsert(
                deltaker = any(),
                erDeltakerSluttdatoEndret = any(),
                nesteStatus = any(),
                beforeDeltakerUpsert = any(),
                afterDeltakerUpsert = any(),
            )
        }
        verify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        coVerify(exactly = 1) { mulighetsrommetApiClient.hentGjennomforingV2(deltakerListe.id) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = deltaker.copy(
            status = deltaker.status.copy(id = deltakerFromDb.status.id, opprettet = deltakerFromDb.status.opprettet),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        deltakerFromDb shouldBe expectedDeltaker

        assertSoftly(importertFraArenaFromDb) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    @Test
    fun `consumeDeltaker - Deltaker eksisterer allerede - lagrer deltaker og importertFraArenaData med ny status`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        val nyStatus = lagDeltakerStatus(statusType = DeltakerStatus.Type.FULLFORT, opprettet = LocalDate.now().atStartOfDay())
        val deltakerMedNyStatus = deltaker.copy(
            status = nyStatus,
            sluttdato = LocalDate.now().minusDays(1),
        )

        TestRepository.insert(deltaker)
        tiltakstypeRepository.upsert(deltakerListe.tiltakstype)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltakerMedNyStatus.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runTest {
            consumer.consumeDeltaker(toPayload(deltakerMedNyStatus))
        }

        coVerify(exactly = 1) {
            deltakerService.transactionalDeltakerUpsert(
                deltaker = any(),
                erDeltakerSluttdatoEndret = false,
                nesteStatus = any(),
                beforeDeltakerUpsert = any(),
                afterDeltakerUpsert = any(),
            )
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = deltakerMedNyStatus.copy(
            status = deltakerMedNyStatus.status.copy(
                id = deltakerFromDb.status.id,
                opprettet = deltakerFromDb.status.opprettet,
            ),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        assertSoftly(importertFraArenaFromDb) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    @Test
    fun `consumeDeltaker - Deltaker eksisterer, lik status, andre endringer pa deltaker - lagrer deltaker`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )

        val endretDeltaker = deltaker.copy(
            sluttdato = LocalDate.now().plusDays(1),
        )

        TestRepository.insert(deltaker)
        tiltakstypeRepository.upsert(deltakerListe.tiltakstype)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = endretDeltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false

        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runTest {
            consumer.consumeDeltaker(toPayload(endretDeltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.transactionalDeltakerUpsert(
                deltaker = any(),
                erDeltakerSluttdatoEndret = any(),
                nesteStatus = any(),
                beforeDeltakerUpsert = any(),
                afterDeltakerUpsert = any(),
            )
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }

        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = endretDeltaker.copy(
            status = endretDeltaker.status.copy(opprettet = deltakerFromDb.status.opprettet),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        assertSoftly(importertFraArenaFromDb) {
            deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
            deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
            importertDato.shouldBeEqualTo(importertFraArena.importertFraArena.importertDato)
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

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

        private fun Deltakerliste.toV2Response() = GjennomforingV2KafkaPayload.Gruppe(
            id = id,
            navn = navn,
            startDato = startDato!!,
            sluttDato = sluttDato,
            status = status!!,
            oppstart = oppstart!!,
            apentForPamelding = apentForPamelding,
            opprettetTidspunkt = OffsetDateTime.now(),
            oppdatertTidspunkt = OffsetDateTime.now(),
            tiltakskode = tiltakstype.tiltakskode,
            antallPlasser = 42,
            tilgjengeligForArrangorFraOgMedDato = startDato,
            deltidsprosent = 42.0,
            oppmoteSted = oppmoteSted,
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangor.organisasjonsnummer),
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
    }
}
