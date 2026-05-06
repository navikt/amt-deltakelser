package no.nav.amt.deltaker.bff.gjennomforing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.arrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.arrangor.ArrangorService
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagArrangorClientResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.TestData.lagGruppeDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.clients.arrangor.ArrangorResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteConsumerTest {
    private val arrangorInTest = lagArrangor()
    private val arrangorResponseInTest: ArrangorResponse = lagArrangorClientResponse(arrangorInTest)

    private val arrangorClient: AmtArrangorClient = mockk(relaxed = true)
    private val arrangorService = ArrangorService(
        repository = ArrangorRepository(),
        amtArrangorClient = arrangorClient,
    )

    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)

    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val tiltakRepository = TiltakRepository()
    private val selfServiceTilgangService: SelfServiceTilgangService = mockk(relaxed = true)
    private val unleashToggle: CommonUnleashToggle = mockk()
    private val navAnsattService = NavAnsattService(
        repository = NavAnsattRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val navEnhetService = NavEnhetService(
        repository = NavEnhetRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockk(relaxed = true),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(relaxed = true),
    )

    private val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = NavBrukerService(
            amtPersonServiceClient = amtPersonServiceClient,
            repository = NavBrukerRepository(),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
        ),
        navEnhetService = navEnhetService,
        paameldingClient = mockk(relaxed = true),
    )

    private val consumer = GjennomforingConsumer(
        deltakerRepository = deltakerRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        arrangorService = arrangorService,
        tiltakRepository = tiltakRepository,
        pameldingService = pameldingService,
        unleashToggle = unleashToggle,
        selfServiceTilgangService = selfServiceTilgangService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns true
        coEvery { arrangorClient.hentArrangor(arrangorResponseInTest.organisasjonsnummer) } returns arrangorResponseInTest
        coEvery { arrangorClient.hentArrangor(arrangorResponseInTest.id) } returns arrangorResponseInTest
    }

    @Test
    fun `endret pameldingstype for deltakerliste med deltakere - skal kaste unntak`() {
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest, pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING)
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagGruppeDeltakerlistePayload(arrangorInTest, deltakerliste)
            .copy(
                arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
            ).copy(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)

        runTest {
            val thrown = shouldThrow<IllegalArgumentException> {
                consumer.consume(
                    deltakerlistePayload.id,
                    objectMapper.writeValueAsString(deltakerlistePayload),
                )
            }

            thrown.message shouldBe
                "Påmeldingstype kan ikke endres for deltakerliste ${deltakerliste.id} med deltakere"
        }
    }

    @Test
    fun `unleashToggle er ikke enabled for tiltakstype - lagrer ikke deltakerliste`() = runTest {
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns false

        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val expectedDeltakerliste = lagDeltakerliste(arrangor = arrangorInTest, tiltakstype = tiltakstype)
        val deltakerlistePayload = lagGruppeDeltakerlistePayload(arrangorInTest, expectedDeltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        consumer.consume(
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        val thrown = shouldThrow<NoSuchElementException> {
            deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow()
        }

        thrown.message shouldBe "Fant ikke deltakerliste med id ${expectedDeltakerliste.id}"
    }

    @Test
    fun `ny liste gruppe - lagrer deltakerliste`() = runTest {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val expectedDeltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            oppstart = Oppstartstype.LOPENDE,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val deltakerlistePayload = lagGruppeDeltakerlistePayload(arrangorInTest, expectedDeltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        consumer.consume(
            key = deltakerlistePayload.id,
            value = objectMapper.writeValueAsString(deltakerlistePayload),
        )

        deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow() shouldBe expectedDeltakerliste

        verify(exactly = 0) { selfServiceTilgangService.stengTilgangerTilDeltakerliste(any()) }
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            oppstart = Oppstartstype.ENKELTPLASS,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val deltakerlistePayload = lagEnkeltplassDeltakerlistePayload(
            arrangor = arrangorInTest,
            deltakerliste = deltakerliste,
        )

        // Act
        consumer.consume(
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste.copy(
            navn = tiltakstype.navn,
            status = deltakerliste.status,
            startDato = null,
            sluttDato = null,
            antallPlasser = null,
            apentForPamelding = true,
            oppmoteSted = null,
        )

        verify(exactly = 0) { selfServiceTilgangService.stengTilgangerTilDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() = runTest {
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest, pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)
        tiltakRepository.upsert(deltakerliste.tiltak)

        consumer.consume(
            deltakerliste.id,
            objectMapper.writeValueAsString(lagGruppeDeltakerlistePayload(arrangorInTest, deltakerliste)),
        )

        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() = runTest {
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest, pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        consumer.consume(
            deltakerliste.id,
            objectMapper.writeValueAsString(lagGruppeDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
        )

        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() = runTest {
        val deltakerliste = lagDeltakerliste()

        TestRepository.insert(deltakerliste)

        consumer.consume(deltakerliste.id, null)

        deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
    }

    @Test
    fun `consumeDeltakerliste - avbrutt, finnes deltakere - oppdaterer deltakerliste, sletter kladd`() = runTest {
        val deltakerlisteInTest = lagDeltakerliste(arrangor = arrangorInTest, pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)

        TestRepository.insert(deltakerlisteInTest)

        val kladd = TestData.lagDeltakerKladd(deltakerliste = deltakerlisteInTest)
        TestRepository.insert(kladd)

        val deltaker = lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)

        val mutatedDeltakerliste = deltakerlisteInTest.copy(sluttDato = LocalDate.now(), status = GjennomforingStatusType.AVBRUTT)

        consumer.consume(
            deltakerlisteInTest.id,
            objectMapper.writeValueAsString(lagGruppeDeltakerlistePayload(arrangorInTest, mutatedDeltakerliste)),
        )

        deltakerlisteRepository.get(deltakerlisteInTest.id).getOrThrow() shouldBe mutatedDeltakerliste
        deltakerRepository.get(kladd.id).getOrNull() shouldBe null
        deltakerRepository.get(deltaker.id).getOrNull() shouldNotBe null
    }
}
