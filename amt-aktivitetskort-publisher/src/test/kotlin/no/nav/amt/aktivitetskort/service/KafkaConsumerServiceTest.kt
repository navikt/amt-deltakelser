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
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.function.Consumer

@JsonTest
class KafkaConsumerServiceTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val arrangorRepository = mockk<ArrangorRepository>()
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>(relaxed = true)
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val aktivitetskortService = mockk<AktivitetskortService>()
    private val amtArrangorClient = mockk<AmtArrangorClient>()
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val ctx: TestData.MockContext = TestData.MockContext()

    private val offset: Long = 0

    private val kafkaConsumerService = KafkaConsumerService(
        arrangorRepository = arrangorRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        deltakerRepository = deltakerRepository,
        aktivitetskortService = aktivitetskortService,
        amtArrangorClient = amtArrangorClient,
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
            // Arrange
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Modified(ctx.deltaker)
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) } returns ctx.aktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) }
        }

        @Test
        fun `deltaker lagd - publiser melding`() {
            // Arrange
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Created(ctx.deltaker)
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) } returns ctx.aktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == ctx.deltaker.id }) }
        }

        @Test
        fun `deltaker har ingen forandring - ikke publiser melding`() {
            // Arrange
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.NoChange()

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 0) { aktivitetskortService.lagAktivitetskort(any<Deltaker>()) }
        }

        @Test
        fun `deltaker finnes ikke, status feilregistrert - publiserer ikke melding`() {
            // Arrange
            val mockDeltaker = ctx.deltaker.copy(
                status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, gyldigFra = LocalDateTime.now()),
            )
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.upsert(mockDeltaker, offset) } returns RepositoryResult.NoChange()

            // Act
            kafkaConsumerService.handleDeltaker(
                id = mockDeltaker.id,
                deltakerPayload = mockDeltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(mockDeltaker, offset) }
            verify(exactly = 0) { aktivitetskortService.lagAktivitetskort(any<Deltaker>()) }
        }

        @Test
        fun `deltaker finnes, status feilregistrert - publiserer melding`() {
            // Arrange
            val mockDeltaker =
                ctx.deltaker.copy(status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, LocalDateTime.now()))
            val mockAktivitetskort = ctx.aktivitetskort.copy(aktivitetStatus = AktivitetStatus.AVBRUTT)
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.upsert(mockDeltaker, offset) } returns RepositoryResult.Modified(mockDeltaker)
            every { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == mockDeltaker.id }) } returns mockAktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = mockDeltaker.id,
                deltakerPayload = mockDeltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(mockDeltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagAktivitetskort(match<Deltaker> { it.id == mockDeltaker.id }) }
        }
    }

    @Nested
    inner class GjennomforingHendelse {
        @Test
        fun `mottar tombstone for deltakerliste - sletter deltakerliste`() {
            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = null,
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.delete(ctx.deltakerliste.id) }
        }

        @Test
        fun `mottar tombstone for deltakerliste med deltakere - sletter ikke deltakerliste`() {
            // Arrange
            every { deltakerRepository.getAntallDeltakereForDeltakerliste(ctx.deltakerliste.id) } returns 2

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = null,
            )

            // Assert
            verify(exactly = 0) { deltakerlisteRepository.delete(any()) }
        }

        @Test
        fun `deltakerliste modifisert - publiser melding`() {
            // Arrange
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Modified(ctx.deltakerliste)
            justRun { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 1) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
        }

        @Test
        fun `deltakerliste lagd - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Created(ctx.deltakerliste)

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
        }

        @Test
        fun `deltakerliste har ingen forandring - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.NoChange()

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id) }
        }

        @Test
        fun `arrangor er ikke lagret - skal hente arrangor fra amt-arrangor`() {
            // Arrange
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

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = deltakerlistePayload.id,
                value = objectMapper.writeValueAsString(deltakerlistePayload),
            )

            // Assert
            verify(exactly = 2) { arrangorRepository.get(arrangorInTest.organisasjonsnummer) }
            verify(exactly = 1) { amtArrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) }
            verify(exactly = 1) { deltakerlisteRepository.upsert(deltakerlisteInTest) }
        }
    }

    @Nested
    inner class ArrangorHendelse {
        @Test
        fun `arrangor modifisert - publiser melding`() {
            // Arrange
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.Modified(ctx.arrangor)
            justRun { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }

            // Act
            kafkaConsumerService.handleArrangor(
                id = ctx.arrangor.id,
                arrangor = ctx.arrangor.toDto(),
            )

            // Assert
            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 1) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
        }

        @Test
        fun `arrangor lagd - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.Created(ctx.arrangor)

            // Act
            kafkaConsumerService.handleArrangor(
                id = ctx.arrangor.id,
                arrangor = ctx.arrangor.toDto(),
            )

            // Assert
            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
        }

        @Test
        fun `arrangor har ingen forandring - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.upsert(ctx.arrangor) } returns RepositoryResult.NoChange()

            // Act
            kafkaConsumerService.handleArrangor(
                id = ctx.arrangor.id,
                arrangor = ctx.arrangor.toDto(),
            )

            // Assert
            verify(exactly = 1) { arrangorRepository.upsert(ctx.arrangor) }
            verify(exactly = 0) { aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor) }
        }
    }
}
