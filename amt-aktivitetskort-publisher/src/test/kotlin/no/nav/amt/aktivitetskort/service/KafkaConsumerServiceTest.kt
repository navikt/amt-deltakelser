package no.nav.amt.aktivitetskort.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.response.ArrangorMedOverordnetArrangorResponse
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.database.TestData.lagAmtGjennomforingPayload
import no.nav.amt.aktivitetskort.database.TestData.lagArrangor
import no.nav.amt.aktivitetskort.database.TestData.lagDeltakerliste
import no.nav.amt.aktivitetskort.database.TestData.toDto
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.kafka.consumer.toTiltakstype
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.TiltakstypeRepository
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.function.Consumer

class KafkaConsumerServiceTest {
    private val arrangorRepository = mockk<ArrangorRepository>()
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>(relaxed = true)
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val aktivitetskortService = mockk<AktivitetskortService>()
    private val amtArrangorClient = mockk<AmtArrangorClient>()
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val aktivitetskortProducer = mockk<AktivitetskortProducer>(relaxed = true)
    private val tiltakstypeRepository = mockk<TiltakstypeRepository>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val ctx: TestData.MockContext = TestData.MockContext()

    private val offset: Long = 0

    private val kafkaConsumerService = KafkaConsumerService(
        arrangorRepository = arrangorRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        tiltakstypeRepository = tiltakstypeRepository,
        deltakerRepository = deltakerRepository,
        aktivitetskortService = aktivitetskortService,
        amtArrangorClient = amtArrangorClient,
        aktivitetskortProducer = aktivitetskortProducer,
        transactionTemplate = transactionTemplate,
        objectMapper = objectMapper,
        amtDeltakerClient = amtDeltakerClient,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { transactionTemplate.executeWithoutResult(any<Consumer<TransactionStatus>>()) } answers {
            (firstArg() as Consumer<TransactionStatus>).accept(SimpleTransactionStatus())
        }
        every { tiltakstypeRepository.getByTiltakskode(any()) } returns ctx.tiltakstype
        justRun { tiltakstypeRepository.upsert(any()) }
        every { deltakerRepository.getAntallDeltakereForDeltakerliste(any()) } returns 0
    }

    private fun TestData.MockContext.stubDeltakerFraAmtDeltaker(
        deltaker: no.nav.amt.aktivitetskort.domain.DeltakerDbo = this.deltaker,
    ): Deltaker = Deltaker
        .fromDeltakerResponse(
            TestData.lagDeltakerResponse(
                deltaker = deltaker,
                deltakerliste = this.deltakerliste,
                arrangor = this.arrangor,
            ),
        ).also {
            every { amtDeltakerClient.getDeltaker(deltaker.id) } returns TestData.lagDeltakerResponse(
                deltaker = deltaker,
                deltakerliste = this.deltakerliste,
                arrangor = this.arrangor,
            )
        }

    @Nested
    inner class DeltakerHendelse {
        @Test
        fun `deltaker modifisert - publiser melding`() {
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Modified(ctx.deltaker)
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) } returns ctx.aktivitetskort

            kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)

            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) }
            verify(exactly = 1) { aktivitetskortProducer.send(ctx.aktivitetskort) }
        }

        @Test
        fun `deltaker lagd - publiser melding`() {
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Created(ctx.deltaker)
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) } returns ctx.aktivitetskort

            kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)

            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) }
            verify(exactly = 1) { aktivitetskortProducer.send(ctx.aktivitetskort) }
        }

        @Test
        fun `deltaker har ingen forandring - ikke publiser melding`() {
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)

            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 0) { aktivitetskortService.lagAktivitetskort(any<Deltaker>()) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<Aktivitetskort>()) }
        }

        @Test
        fun `deltaker finnes ikke, status feilregistrert - publiserer ikke melding`() {
            val mockDeltaker = ctx.deltaker.copy(
                status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, gyldigFra = LocalDateTime.now()),
            )
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.upsert(mockDeltaker, offset) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleDeltaker(mockDeltaker.id, mockDeltaker.toDto(), offset)

            verify(exactly = 1) { deltakerRepository.upsert(mockDeltaker, offset) }
            verify(exactly = 0) { aktivitetskortService.lagAktivitetskort(any<Deltaker>()) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<Aktivitetskort>()) }
        }

        @Test
        fun `deltaker finnes, status feilregistrert - publiserer melding`() {
            val mockDeltaker =
                ctx.deltaker.copy(status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, LocalDateTime.now()))
            val mockAktivitetskort = ctx.aktivitetskort.copy(aktivitetStatus = AktivitetStatus.AVBRUTT)
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.upsert(mockDeltaker, offset) } returns RepositoryResult.Modified(mockDeltaker)
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == mockDeltaker.id }) } returns mockAktivitetskort

            kafkaConsumerService.handleDeltaker(mockDeltaker.id, mockDeltaker.toDto(), offset)

            verify(exactly = 1) { deltakerRepository.upsert(mockDeltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == mockDeltaker.id }) }
            verify(exactly = 1) { aktivitetskortProducer.send(mockAktivitetskort) }
        }
    }

    @Nested
    inner class GjennomforingHendelse {
        @Test
        fun `mottar tombstone for deltakerliste - sletter deltakerliste`() {
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = null,
            )

            verify(exactly = 1) { deltakerlisteRepository.delete(ctx.deltakerliste.id) }
        }

        @Test
        fun `mottar tombstone for deltakerliste med deltakere - sletter ikke deltakerliste`() {
            every { deltakerRepository.getAntallDeltakereForDeltakerliste(ctx.deltakerliste.id) } returns 2

            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = null,
            )

            verify(exactly = 0) { deltakerlisteRepository.delete(any()) }
        }

        @Test
        fun `deltakerliste modifisert - publiser melding`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Modified(ctx.deltakerliste)
            every { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) } returns listOf(ctx.aktivitetskort)

            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 1) { tiltakstypeRepository.upsert(ctx.amtGjennomforingPayload.toTiltakstype()) }
            verify(exactly = 1) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
            verify(exactly = 1) { aktivitetskortProducer.send(listOf(ctx.aktivitetskort)) }
        }

        @Test
        fun `deltakerliste lagd - ikke publiser melding`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Created(ctx.deltakerliste)

            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<List<Aktivitetskort>>()) }
        }

        @Test
        fun `deltakerliste har ingen forandring - ikke publiser melding`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<List<Aktivitetskort>>()) }
        }

        @Test
        fun `arrangor er ikke lagret - skal hente arrangor fra amt-arrangor`() {
            val arrangorInTest = lagArrangor()
            val deltakerlisteInTest =
                lagDeltakerliste(
                    tiltak = Tiltak("Oppfølging", Tiltakskode.OPPFOLGING),
                    arrangorId = arrangorInTest.id,
                )

            every { arrangorRepository.get(arrangorInTest.organisasjonsnummer) } returns null andThen arrangorInTest
            every { arrangorRepository.upsert(any()) } returns RepositoryResult.Created(arrangorInTest)
            every { amtArrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns
                ArrangorMedOverordnetArrangorResponse(
                    arrangorInTest.id,
                    arrangorInTest.navn,
                    arrangorInTest.organisasjonsnummer,
                    null,
                )
            every { deltakerlisteRepository.upsert(deltakerlisteInTest) } returns RepositoryResult.Created(deltakerlisteInTest)

            val deltakerlistePayload = lagAmtGjennomforingPayload(
                arrangor = arrangorInTest,
                deltakerliste = deltakerlisteInTest,
            )

            kafkaConsumerService.handleGjennomforing(
                id = deltakerlistePayload.id,
                value = objectMapper.writeValueAsString(deltakerlistePayload),
            )

            verify(exactly = 2) { arrangorRepository.get(arrangorInTest.organisasjonsnummer) }
            verify(exactly = 1) { amtArrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) }
            verify(exactly = 1) { deltakerlisteRepository.upsert(deltakerlisteInTest) }
        }
    }

    @Nested
    inner class ArrangorHendelse {
        @Test
        fun `arrangor modifisert - publiser melding`() {
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.Modified(ctx.arrangor)
            every { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) } returns listOf(ctx.aktivitetskort)

            kafkaConsumerService.handleArrangor(ctx.arrangor.id, ctx.arrangor.toDto())

            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 1) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
            verify(exactly = 1) { aktivitetskortProducer.send(listOf(ctx.aktivitetskort)) }
        }

        @Test
        fun `arrangor lagd - ikke publiser melding`() {
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.Created(ctx.arrangor)

            kafkaConsumerService.handleArrangor(ctx.arrangor.id, ctx.arrangor.toDto())

            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<List<Aktivitetskort>>()) }
        }

        @Test
        fun `arrangor har ingen forandring - ikke publiser melding`() {
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleArrangor(ctx.arrangor.id, ctx.arrangor.toDto())

            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortProducer.send(any<List<Aktivitetskort>>()) }
        }
    }
}
