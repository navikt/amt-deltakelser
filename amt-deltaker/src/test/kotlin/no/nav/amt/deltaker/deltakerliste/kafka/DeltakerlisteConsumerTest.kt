package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteConsumerTest {
    private val arrangorInTest = lagArrangor()

    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val deltakerRepository = DeltakerRepository()
    private val tiltakstypeRepository = TiltakstypeRepository()
    private val deltakerService: DeltakerService = mockk(relaxed = true)
    private val arrangorRepository = ArrangorRepository()
    private val arrangorService = ArrangorService(arrangorRepository, mockAmtArrangorClient(arrangorInTest))
    private val unleashToggle: CommonUnleashToggle = mockk()

    private val consumer =
        DeltakerlisteConsumer(
            deltakerlisteRepository = deltakerlisteRepository,
            deltakerRepository = deltakerRepository,
            tiltakstypeRepository = tiltakstypeRepository,
            arrangorService = arrangorService,
            deltakerService = deltakerService,
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

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagDeltakerlistePayload(arrangorInTest, deltakerliste)
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
    fun `unleashToggle er ikke enabled for tiltakstype - lagrer ikke deltakerliste`() {
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns false

        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakstypeRepository.upsert(tiltakstype)

        val expectedDeltakerliste = lagDeltakerliste(arrangor = arrangorInTest, tiltakstype = tiltakstype)

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagDeltakerlistePayload(arrangorInTest, expectedDeltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        runTest {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            val thrown = shouldThrow<NoSuchElementException> {
                deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow()
            }

            thrown.message shouldBe "Fant ikke deltakerliste med id ${expectedDeltakerliste.id}"
        }
    }

    @Test
    fun `ny liste v2 gruppe - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakstypeRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        val deltakerlistePayload = lagDeltakerlistePayload(arrangorInTest, deltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        runTest {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
        tiltakstypeRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            gjennomforingstype = GjennomforingType.Enkeltplass,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val deltakerlistePayload = lagEnkeltplassDeltakerlistePayload(arrangorInTest, deltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        runTest {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste.copy(
                navn = "Test tiltak ${deltakerliste.tiltakstype.tiltakskode}",
                status = null,
                startDato = null,
                sluttDato = null,
                oppstart = null,
                oppmoteSted = null,
            )
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype()
        tiltakstypeRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        runTest {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, deltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() {
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runTest {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - avbrutt - oppdaterer deltakerliste og avslutter deltakere`() {
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now(), status = GjennomforingStatusType.AVBRUTT)

        runTest {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avsluttDeltakelserPaaDeltakerliste(oppdatertDeltakerliste) }
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() {
        val deltakerliste = lagDeltakerliste()

        TestRepository.insert(deltakerliste)

        runTest {
            consumer.consume(deltakerliste.id, null)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - redusert sluttdato - oppdaterer deltakerliste og oppdaterer sluttdato pa deltakere`() {
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runTest {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste) }
    }
}
