package no.nav.amt.deltaker.bff.deltakerliste.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.arrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.arrangor.ArrangorService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.data.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.data.TestData.lagGruppeDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.bff.utils.mockAmtDeltakerClient
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.deltaker.bff.utils.mockPaameldingClient
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

    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangorInTest))
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val tiltakstypeRepository = TiltakstypeRepository()
    private val tilgangskontrollService: TilgangskontrollService = mockk(relaxed = true)
    private val unleashToggle: CommonUnleashToggle = mockk()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClient())
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClient())
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockAmtDeltakerClient(),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(relaxed = true),
    )

    private val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = NavBrukerService(mockAmtPersonServiceClient(), NavBrukerRepository(), navAnsattService, navEnhetService),
        navEnhetService = navEnhetService,
        paameldingClient = mockPaameldingClient(),
    )

    private val consumer =
        DeltakerlisteConsumer(
            deltakerRepository = deltakerRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            arrangorService = arrangorService,
            tiltakstypeRepository = tiltakstypeRepository,
            pameldingService = pameldingService,
            tilgangskontrollService = tilgangskontrollService,
            unleashToggle = unleashToggle,
        )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns true
    }

    @Test
    fun `endret pameldingstype for deltakerliste med deltakere - skal kaste unntak`() {
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest)
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
        tiltakstypeRepository.upsert(tiltakstype)

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
        tiltakstypeRepository.upsert(tiltakstype)

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
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow() shouldBe expectedDeltakerliste

        verify(exactly = 0) { tilgangskontrollService.stengTilgangerTilDeltakerliste(any()) }
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() = runTest {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
        tiltakstypeRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val deltakerlistePayload = lagEnkeltplassDeltakerlistePayload(arrangorInTest, deltakerliste)

        consumer.consume(
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste.copy(
            navn = tiltakstype.navn,
            status = null,
            startDato = null,
            sluttDato = null,
            oppstart = null,
            antallPlasser = null,
            apentForPamelding = true,
            oppmoteSted = null,
        )

        verify(exactly = 0) { tilgangskontrollService.stengTilgangerTilDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() = runTest {
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest, pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)
        tiltakstypeRepository.upsert(deltakerliste.tiltak)

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

        MockResponseHandler.addSlettKladdResponse(kladd.id)

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
