package no.nav.amt.deltaker.kafka

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GjennomforingConsumerTest {
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>()
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val tiltakRepository = mockk<TiltakRepository>()
    private val arrangorService = mockk<ArrangorService>()
    private val deltakerService = mockk<DeltakerService>()
    private val deltakerProducerService = mockk<DeltakerProducerService>()
    private val unleashToggle = mockk<CommonUnleashToggle>()

    private val consumer = GjennomforingConsumer(
        deltakerlisteRepository = deltakerlisteRepository,
        deltakerRepository = deltakerRepository,
        tiltakRepository = tiltakRepository,
        arrangorService = arrangorService,
        deltakerService = deltakerService,
        deltakerProducerService = deltakerProducerService,
        unleashToggle = unleashToggle,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        mockkObject(Database)
        every { Database.transaction<Any>(any()) } answers {
            firstArg<() -> Any>().invoke()
        }
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns true

        coEvery { arrangorService.hentArrangor(any<String>()) } answers {
            lagArrangor(organisasjonsnummer = firstArg())
        }

        every { tiltakRepository.get(any<Tiltakskode>()) } answers {
            Result.success(lagTiltakstype(tiltakskode = firstArg()))
        }

        every { deltakerlisteRepository.upsert(any<Deltakerliste>()) } just runs
        every { deltakerRepository.getAntallDeltakereForDeltakerliste(any()) } returns 0
        every { deltakerProducerService.produce(any<Deltaker>(), any<Boolean>()) } just runs
        every { deltakerProducerService.produce(any<Deltaker>()) } just runs
        every { deltakerService.avsluttDeltakere(any<List<Deltaker>>()) } just runs
    }

    @AfterEach
    fun cleanup() = unmockkObject(Database)

    private fun stubEksisterendeDeltakerliste(deltakerliste: Deltakerliste) {
        deltakerliste.arrangor?.let { arrangor ->
            coEvery { arrangorService.hentArrangor(arrangor.organisasjonsnummer) } returns arrangor
        }
        every { deltakerlisteRepository.get(deltakerliste.id) } returns Result.success(deltakerliste)
    }

    private suspend fun consumePayloadFor(deltakerliste: Deltakerliste) {
        val payload = when (deltakerliste.gjennomforingstype) {
            GjennomforingType.Enkeltplass -> lagEnkeltplassDeltakerlistePayload(
                deltakerliste = deltakerliste.copy(
                    pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
                ),
            ).copy(
                id = deltakerliste.id,
                status = deltakerliste.status,
            )

            GjennomforingType.Gruppe -> lagDeltakerlistePayload(deltakerliste = deltakerliste)
        }

        consumer.consume(
            key = deltakerliste.id,
            value = objectMapper.writeValueAsString(payload),
        )
    }

    @Nested
    inner class PubliserEnkeltplassDeltakerTest {
        @Test
        fun `skal produsere deltaker når gjennomforing er enkeltplass i KLADD`() {
            // Arrange
            val enkeltplassDeltakerliste = lagEnkeltplassDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = enkeltplassDeltakerliste,
            )
            every { deltakerRepository.getEnkeltplassdeltaker(enkeltplassDeltakerliste.id) } returns Result.success(deltaker)

            // Act
            consumer.publiserEnkeltplassDeltaker(enkeltplassDeltakerliste)

            // Assert
            verify { deltakerProducerService.produce(deltaker) }
        }

        @Test
        fun `skal ikke hente eller produsere deltaker nar gjennomforing ikke er KLADD`() {
            // Arrange
            val enkeltplassDeltakerliste = lagEnkeltplassDeltakerliste(GjennomforingStatusType.GJENNOMFORES)

            // Act
            consumer.publiserEnkeltplassDeltaker(enkeltplassDeltakerliste)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
            verify(exactly = 0) { deltakerProducerService.produce(any<Deltaker>()) }
        }

        @Test
        fun `skal ikke gjøre noe for gruppedeltakerliste`() {
            // Arrange
            val gruppeDeltakerliste = lagGruppeDeltakerliste()

            // Act
            consumer.publiserEnkeltplassDeltaker(gruppeDeltakerliste)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
            verify(exactly = 0) { deltakerProducerService.produce(any<Deltaker>()) }
        }
    }

    @Nested
    inner class GruppeDeltakerlisteTest {
        @Test
        fun `skal ikke kalle handterDeltakere for Arena-enkeltplass tiltakskoder`() = runTest {
            // Arrange
            val gruppeDeltakerliste = lagGruppeDeltakerliste()
            val arenaEnkeltplassTiltakstype = lagTiltakstype(
                tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            )

            every { tiltakRepository.get(any()) } returns Result.success(arenaEnkeltplassTiltakstype)
            stubEksisterendeDeltakerliste(gruppeDeltakerliste)

            // Act
            consumePayloadFor(gruppeDeltakerliste)

            // Assert
            verify(exactly = 0) { deltakerService.avsluttDeltakere(any()) }
        }

        @Test
        fun `skal kalle handterDeltakere for ordinære gruppetiltakskoder`() = runTest {
            // Arrange
            val aktivGruppeDeltakerliste = lagGruppeDeltakerliste()
            val avbruttGruppeDeltakerliste = lagGruppeDeltakerliste(
                status = GjennomforingStatusType.AVBRUTT,
            ).copy(id = aktivGruppeDeltakerliste.id)
            val deltaker = lagDeltaker(deltakerliste = aktivGruppeDeltakerliste)

            every { tiltakRepository.get(any()) } returns
                Result.success(lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING))
            stubEksisterendeDeltakerliste(aktivGruppeDeltakerliste)
            every { deltakerRepository.getDeltakereForAvsluttetDeltakerliste(aktivGruppeDeltakerliste.id) } returns listOf(deltaker)

            // Act
            consumePayloadFor(avbruttGruppeDeltakerliste)

            // Assert
            verify { deltakerService.avsluttDeltakere(any()) }
        }

        @Test
        fun `skal avslutte deltakere når gruppedeltakerliste blir avbrutt`() = runTest {
            // Arrange
            val aktivGruppeDeltakerliste = lagGruppeDeltakerliste()
            val avbruttGruppeDeltakerliste = lagGruppeDeltakerliste(
                status = GjennomforingStatusType.AVBRUTT,
            ).copy(id = aktivGruppeDeltakerliste.id)
            val deltaker = lagDeltaker(deltakerliste = aktivGruppeDeltakerliste)

            every { tiltakRepository.get(any()) } returns Result.success(lagTiltakstype())
            stubEksisterendeDeltakerliste(aktivGruppeDeltakerliste)
            every { deltakerRepository.getDeltakereForAvsluttetDeltakerliste(aktivGruppeDeltakerliste.id) } returns listOf(deltaker)

            // Act
            consumePayloadFor(avbruttGruppeDeltakerliste)

            // Assert
            verify { deltakerService.avsluttDeltakere(any()) }
        }
    }

    @Nested
    inner class UnchangedDeltakerlisteTest {
        @Test
        fun `skal ikke handtere deltakere hvis deltakerlisten er uendret`() = runTest {
            // Arrange
            val deltakerliste = lagGruppeDeltakerliste()

            stubEksisterendeDeltakerliste(deltakerliste)

            // Act
            consumePayloadFor(deltakerliste)

            // Assert
            verify(exactly = 0) { deltakerService.avsluttDeltakere(any()) }
            verify(exactly = 0) { deltakerProducerService.produce(any<Deltaker>(), any()) }
        }

        @Test
        fun `skal publisere enkeltplassdeltaker selv om deltakerlisten er uendret`() = runTest {
            // Arrange
            val enkeltplassDeltakerliste = lagEnkeltplassDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = enkeltplassDeltakerliste,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.SOKT_INN),
            )

            stubEksisterendeDeltakerliste(enkeltplassDeltakerliste)
            every { deltakerRepository.getEnkeltplassdeltaker(enkeltplassDeltakerliste.id) } returns Result.success(deltaker)

            // Act
            consumePayloadFor(enkeltplassDeltakerliste)

            // Assert — publiserEnkeltplassDeltaker is called even though deltakerliste is unchanged
            verify { deltakerProducerService.produce(deltaker) }
            // Assert — handterDeltakere is NOT called
            verify(exactly = 0) { deltakerService.avsluttDeltakere(any()) }
        }
    }

    @Nested
    inner class AvgrensSluttdatoerTilTest {
        @Test
        fun `avgrensSluttdatoerTil - deltaker har senere sluttdato enn deltakerliste - forcedUpdate er true`() {
            // Arrange
            val originalSluttDato = LocalDate.now().plusDays(60)
            val nySluttDato = LocalDate.now().plusDays(30)

            val originalDeltakerliste = lagGruppeDeltakerliste().copy(
                sluttDato = originalSluttDato,
            )

            val deltaker = lagDeltaker(
                deltakerliste = originalDeltakerliste,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
                sluttdato = originalSluttDato.plusDays(30), // Deltaker har senere sluttdato
            )

            val oppdatertDeltakerliste = originalDeltakerliste.copy(
                sluttDato = nySluttDato,
            )

            // Mock the repository calls
            every { deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(originalDeltakerliste.id) } returns listOf(deltaker)
            every { deltakerRepository.upsert(any<Deltaker>()) } just runs
            every { deltakerRepository.get(deltaker.id) } returns Result.success(
                deltaker.copy(sluttdato = nySluttDato),
            )
            every { deltakerService.lagreDeltakerStatus(any(), any(), any()) } returns lagDeltakerStatus()

            // Act
            consumer.avgrensSluttdatoerTil(oppdatertDeltakerliste)

            // Assert
            verify {
                deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(originalDeltakerliste.id)
            }
            verify {
                deltakerRepository.upsert(
                    match { it.id == deltaker.id && it.sluttdato == nySluttDato },
                )
            }
            verify {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = deltaker.status,
                    erDeltakerSluttdatoEndret = true,
                )
            }
            verify {
                deltakerProducerService.produce(
                    deltaker = any<Deltaker>(),
                    forcedUpdate = true,
                )
            }
        }

        @Test
        fun `avgrensSluttdatoerTil - deltaker har tidligere sluttdato enn deltakerliste - deltakers sluttdato endres ikke`() {
            // Arrange
            val originalSluttDato = LocalDate.now().plusDays(60)
            val nySluttDato = LocalDate.now().plusDays(30)

            val originalDeltakerliste = lagGruppeDeltakerliste().copy(
                sluttDato = originalSluttDato,
            )

            val oppdatertDeltakerliste = originalDeltakerliste.copy(
                sluttDato = nySluttDato,
            )

            // Mock the repository calls
            every { deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(originalDeltakerliste.id) } returns emptyList()

            // Act
            consumer.avgrensSluttdatoerTil(oppdatertDeltakerliste)

            // Assert - verify no updates were made
            verify(exactly = 0) {
                deltakerRepository.upsert(any<Deltaker>())
            }
            verify(exactly = 0) {
                deltakerService.lagreDeltakerStatus(any(), any(), any())
            }
            verify(exactly = 0) {
                deltakerProducerService.produce(any<Deltaker>(), any<Boolean>())
            }
        }
    }

    companion object {
        private fun lagEnkeltplassDeltakerliste(status: GjennomforingStatusType = GjennomforingStatusType.KLADD) = lagDeltakerliste(
            status = status,
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
            gjennomforingstype = GjennomforingType.Enkeltplass,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        private fun lagGruppeDeltakerliste(status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES) = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Gruppe,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
            status = status,
        )
    }
}
