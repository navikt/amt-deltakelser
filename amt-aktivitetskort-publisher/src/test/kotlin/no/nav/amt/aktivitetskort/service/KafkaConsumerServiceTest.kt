package no.nav.amt.aktivitetskort.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
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
    private val forberedtMelding = AktivitetskortService.ForberedtMelding.Klar(ctx.melding, TestData.oppfolgingsperiode())
    private val forberedtAktivitetskort = AktivitetskortService.ForberedtAktivitetskort(ctx.melding, ctx.melding, forberedtMelding)

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
        every { deltakerRepository.skalOppdatere(any(), any(), any()) } returns true
        every { aktivitetskortService.forberedMelding(any()) } returns forberedtMelding
        every { aktivitetskortService.getSisteMeldingForDeltaker(any()) } returns null
        justRun { aktivitetskortService.laasMeldingshistorikk(any()) }
        justRun { aktivitetskortService.kontrollerMeldingshistorikk(any()) }
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
            every { aktivitetskortService.lagreAktivitetskort(forberedtMelding) } returns ctx.aktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagreAktivitetskort(forberedtMelding) }
            verifyOrder {
                aktivitetskortService.getSisteMeldingForDeltaker(ctx.deltaker.id)
                aktivitetskortService.forberedMelding(any())
                transactionTemplate.executeWithoutResult(any())
                deltakerRepository.upsert(ctx.deltaker, offset)
                aktivitetskortService.getSisteMeldingForDeltaker(ctx.deltaker.id)
                aktivitetskortService.lagreAktivitetskort(forberedtMelding)
            }
        }

        @Test
        fun `deltaker lagd - publiser melding`() {
            // Arrange
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Created(ctx.deltaker)
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.lagreAktivitetskort(forberedtMelding) } returns ctx.aktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(ctx.deltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagreAktivitetskort(forberedtMelding) }
        }

        @Test
        fun `deltaker har ingen forandring - ikke publiser melding`() {
            // Arrange
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.skalOppdatere(ctx.deltaker, offset) } returns false

            // Act
            kafkaConsumerService.handleDeltaker(
                id = ctx.deltaker.id,
                deltakerPayload = ctx.deltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 0) { deltakerRepository.upsert(any(), any(), any()) }
            verify(exactly = 0) { aktivitetskortService.forberedMelding(any()) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `deltaker finnes ikke, status feilregistrert - publiserer ikke melding`() {
            // Arrange
            val mockDeltaker = ctx.deltaker.copy(
                status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, gyldigFra = LocalDateTime.now()),
            )
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.skalOppdatere(mockDeltaker, offset) } returns false

            // Act
            kafkaConsumerService.handleDeltaker(
                id = mockDeltaker.id,
                deltakerPayload = mockDeltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 0) { deltakerRepository.upsert(any(), any(), any()) }
            verify(exactly = 0) { aktivitetskortService.forberedMelding(any()) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `deltaker finnes, status feilregistrert - publiserer melding`() {
            // Arrange
            val mockDeltaker =
                ctx.deltaker.copy(status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null, LocalDateTime.now()))
            val mockAktivitetskort = ctx.aktivitetskort.copy(aktivitetStatus = AktivitetStatus.AVBRUTT)
            ctx.stubDeltakerFraAmtDeltaker(mockDeltaker)

            every { deltakerRepository.upsert(mockDeltaker, offset) } returns RepositoryResult.Modified(mockDeltaker)
            every { aktivitetskortService.lagreAktivitetskort(forberedtMelding) } returns mockAktivitetskort

            // Act
            kafkaConsumerService.handleDeltaker(
                id = mockDeltaker.id,
                deltakerPayload = mockDeltaker.toDto(),
                offset = offset,
            )

            // Assert
            verify(exactly = 1) { deltakerRepository.upsert(mockDeltaker, offset) }
            verify(exactly = 1) { aktivitetskortService.lagreAktivitetskort(forberedtMelding) }
        }

        @Test
        fun `NoChange ved siste sjekk forkaster forberedt melding`() {
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)

            verify(exactly = 1) { aktivitetskortService.forberedMelding(any()) }
            verify(exactly = 1) { aktivitetskortService.getSisteMeldingForDeltaker(ctx.deltaker.id) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `endret meldingshistorikk etter upsert forkaster forberedelsen`(endretPeriode: Boolean) {
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Modified(ctx.deltaker)
            val nyMelding = if (endretPeriode) {
                ctx.melding.copy(oppfolgingperiode = UUID.randomUUID())
            } else {
                val nyId = UUID.randomUUID()
                ctx.melding.copy(id = nyId, aktivitetskort = ctx.aktivitetskort.copy(id = nyId))
            }
            every { aktivitetskortService.getSisteMeldingForDeltaker(ctx.deltaker.id) } returnsMany listOf(
                ctx.melding,
                nyMelding,
            )

            val feil = shouldThrow<ConcurrencyFailureException> {
                kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)
            }

            feil.message shouldBe
                "Meldingshistorikken for deltaker ${ctx.deltaker.id} er endret. Aktivitetskortet må forberedes på nytt."
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `endret modifiedAt pa samme kort og periode forkaster forberedelsen`() {
            ctx.stubDeltakerFraAmtDeltaker()
            every { deltakerRepository.upsert(ctx.deltaker, offset) } returns RepositoryResult.Modified(ctx.deltaker)
            every { aktivitetskortService.getSisteMeldingForDeltaker(ctx.deltaker.id) } returnsMany listOf(
                ctx.melding,
                ctx.melding.copy(modifiedAt = ctx.melding.modifiedAt.plusSeconds(1)),
            )

            shouldThrow<ConcurrencyFailureException> {
                kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)
            }

            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `teknisk feil ved forberedelse starter ikke transaksjon`() {
            ctx.stubDeltakerFraAmtDeltaker()
            every { aktivitetskortService.forberedMelding(any()) } throws IllegalStateException("API-feil")

            shouldThrow<IllegalStateException> {
                kafkaConsumerService.handleDeltaker(ctx.deltaker.id, ctx.deltaker.toDto(), offset)
            }

            verify(exactly = 0) { transactionTemplate.executeWithoutResult(any()) }
            verify(exactly = 0) { deltakerRepository.upsert(any(), any(), any()) }
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
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste.copy(navn = "Tidligere navn")
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Modified(ctx.deltakerliste)
            every { aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id) } returns listOf(forberedtAktivitetskort)
            every { aktivitetskortService.lagreAktivitetskort(forberedtMelding) } returns ctx.aktivitetskort

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verifyOrder {
                aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id)
                transactionTemplate.executeWithoutResult(any())
                deltakerlisteRepository.upsert(ctx.deltakerliste)
                aktivitetskortService.lagreAktivitetskort(forberedtMelding)
            }
        }

        @Test
        fun `deltakerliste lagd - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns null
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Created(ctx.deltakerliste)

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 1) { deltakerlisteRepository.upsert(ctx.deltakerliste) }
            verify(exactly = 0) { aktivitetskortService.forberedAktivitetskort(any()) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `deltakerliste har ingen forandring - ikke publiser melding`() {
            // Arrange
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste

            // Act
            kafkaConsumerService.handleGjennomforing(
                id = ctx.amtGjennomforingPayload.id,
                value = objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            // Assert
            verify(exactly = 0) { deltakerlisteRepository.upsert(any()) }
            verify(exactly = 0) { aktivitetskortService.forberedAktivitetskort(any()) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `deltakerliste uendret ved siste sjekk - forkast forberedte meldinger`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste.copy(navn = "Tidligere navn")
            every { aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id) } returns listOf(forberedtAktivitetskort)
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.NoChange()

            kafkaConsumerService.handleGjennomforing(
                ctx.deltakerliste.id,
                objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
            )

            verify(exactly = 1) { aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id) }
            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `deltakerliste opprettet under forberedelse - feil fremfor å hoppe over kortoppdatering`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns null
            every { deltakerlisteRepository.upsert(ctx.deltakerliste) } returns RepositoryResult.Modified(ctx.deltakerliste)

            shouldThrow<IllegalStateException> {
                kafkaConsumerService.handleGjennomforing(
                    ctx.deltakerliste.id,
                    objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
                )
            }

            verify(exactly = 0) { aktivitetskortService.lagreAktivitetskort(any()) }
        }

        @Test
        fun `teknisk feil ved forberedelse av gjennomføring starter ikke transaksjon`() {
            every { arrangorRepository.get(ctx.arrangor.organisasjonsnummer) } returns ctx.arrangor
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste.copy(navn = "Tidligere navn")
            every { aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id) } throws IllegalStateException("API-feil")

            shouldThrow<IllegalStateException> {
                kafkaConsumerService.handleGjennomforing(
                    ctx.deltakerliste.id,
                    objectMapper.writeValueAsString(ctx.amtGjennomforingPayload),
                )
            }

            verify(exactly = 0) { transactionTemplate.executeWithoutResult(any()) }
            verify(exactly = 0) { deltakerlisteRepository.upsert(any()) }
        }

        @Test
        fun `arrangør er ikke lagret - skal hente arrangør fra amt-arrangor`() {
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
            every { deltakerlisteRepository.get(deltakerlisteInTest.id) } returns null

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
        fun `arrangør modifisert - publiser melding`() {
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
        fun `arrangør lagd - ikke publiser melding`() {
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
        fun `arrangør har ingen forandring - ikke publiser melding`() {
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
