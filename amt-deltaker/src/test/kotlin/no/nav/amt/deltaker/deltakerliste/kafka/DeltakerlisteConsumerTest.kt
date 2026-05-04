package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData.lagArrangorResponse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerlisteConsumerTest : IntegrationTestWithDbBase() {
    override val deltakerProducerService: DeltakerProducerService = mockk()

    private val arrangorInTest = lagArrangor()

    @BeforeEach
    fun setupMocks() {
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns true
    }

    @Nested
    inner class EnkeltplassTests {
        private val deltakerlisteInTest = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            gjennomforingstype = GjennomforingType.Enkeltplass,
            oppstart = Oppstartstype.ENKELTPLASS,
            status = GjennomforingStatusType.KLADD,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
            arrangor = arrangorInTest,
        )

        private val deltakerInTest = lagDeltaker(
            deltakerliste = deltakerlisteInTest,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )

        private fun arrange(isHappyPathTest: Boolean = true) {
            if (isHappyPathTest) {
                lagVedtak(deltakerVedVedtak = deltakerInTest).let { vedtak ->
                    TestRepository.insertAll(
                        deltakerInTest,
                        lagNavEnhet(id = vedtak.opprettetAvEnhet),
                        lagNavAnsatt(id = vedtak.opprettetAv),
                        vedtak,
                    )
                }
            } else {
                TestRepository.insertAll(deltakerlisteInTest)
            }
        }

        @BeforeEach
        fun setup() {
            every {
                deltakerProducerService.produce(any(), any(), any(), any(), any())
            } just Runs
        }

        @Test
        fun `skal kaste feil hvis enkeltplassdeltaker ikke finnes i db`() = runTest {
            // Arrange
            arrange(false)

            val enkeltplassPayloadInTest = lagEnkeltplassDeltakerlistePayload(
                arrangor = arrangorInTest,
                deltakerliste = deltakerlisteInTest.copy(
                    status = GjennomforingStatusType.GJENNOMFORES,
                ),
            )

            // Act & Assert
            shouldThrow<NoSuchElementException> {
                deltakerlisteConsumer.consume(
                    key = enkeltplassPayloadInTest.id,
                    value = objectMapper.writeValueAsString(enkeltplassPayloadInTest),
                )
            }
        }

        @Test
        fun `skal produsere deltaker pa topics nar status for gjennomforing er endret fra kladd`() = runTest {
            // Arrange
            arrange()
            val enkeltplassPayloadInTest = lagEnkeltplassDeltakerlistePayload(
                arrangor = arrangorInTest,
                deltakerliste = deltakerlisteInTest.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )

            // Act
            deltakerlisteConsumer.consume(
                key = enkeltplassPayloadInTest.id,
                value = objectMapper.writeValueAsString(enkeltplassPayloadInTest),
            )

            // Assert
            verify {
                deltakerProducerService.produce(
                    deltaker = match { it.id == deltakerInTest.id },
                    forcedUpdate = any(),
                    publiserTilDeltakerV1 = any(),
                    publiserTilDeltakerEksternV1 = any(),
                    publiserTilDeltakerV2 = any(),
                )
            }
        }

        @Test
        fun `skal ikke produsere deltaker pa topics nar status for gjennomforing er uendret`() = runTest {
            // Arrange
            arrange()
            val enkeltplassPayloadInTest = lagEnkeltplassDeltakerlistePayload(
                arrangor = arrangorInTest,
                deltakerliste = deltakerlisteInTest,
            )

            // Act
            deltakerlisteConsumer.consume(
                key = enkeltplassPayloadInTest.id,
                value = objectMapper.writeValueAsString(enkeltplassPayloadInTest),
            )

            // Assert
            verify(exactly = 0) {
                deltakerProducerService.produce(
                    deltaker = any(),
                    forcedUpdate = any(),
                    publiserTilDeltakerV1 = any(),
                    publiserTilDeltakerEksternV1 = any(),
                    publiserTilDeltakerV2 = any(),
                )
            }
        }
    }

    @Nested
    inner class AvgrensSluttdatoerTilTests {
        @Test
        fun `avgrensSluttdatoerTil - deltaker har senere sluttdato enn deltakerliste - deltakers sluttdato endres`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = deltakerliste.sluttDato!!.plusMonths(1),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)

            TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

            every {
                deltakerProducerService.produce(any(), any(), any(), any(), any())
            } just Runs

            // Act
            deltakerlisteConsumer.avgrensSluttdatoerTil(deltakerliste)

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.sluttdato shouldBe deltakerliste.sluttDato
        }

        @Test
        fun `avgrensSluttdatoerTil - deltaker har tidligere sluttdato enn deltakerliste - deltakers sluttdato endres ikke`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = deltakerliste.sluttDato!!.minusDays(1),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)

            TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

            // Act
            deltakerlisteConsumer.avgrensSluttdatoerTil(deltakerliste)

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.sluttdato shouldNotBe deltakerliste.sluttDato
        }
    }

    @Test
    fun `endret pameldingstype for deltakerliste med deltakere - skal kaste unntak`() = runTest {
        // Arrange
        val deltakerliste = lagDeltakerliste(arrangor = arrangorInTest)
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagDeltakerlistePayload(arrangorInTest, deltakerliste)
            .copy(
                arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
            ).copy(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)

        // Act
        val thrown = shouldThrow<IllegalArgumentException> {
            deltakerlisteConsumer.consume(
                key = deltakerlistePayload.id,
                value = objectMapper.writeValueAsString(deltakerlistePayload),
            )
        }

        // Assert
        thrown.message shouldBe
            "Påmeldingstype kan ikke endres for deltakerliste ${deltakerliste.id} med deltakere"
    }

    @Test
    fun `unleashToggle er ikke enabled for tiltakstype - lagrer ikke deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val expectedDeltakerliste = lagDeltakerliste(arrangor = arrangorInTest, tiltakstype = tiltakstype)

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagDeltakerlistePayload(arrangorInTest, expectedDeltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        every { unleashToggle.skalLeseGjennomforing(tiltakstype.tiltakskode.name) } returns false
        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerlistePayload.id,
            value = objectMapper.writeValueAsString(deltakerlistePayload),
        )

        val thrown = shouldThrow<NoSuchElementException> {
            deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow()
        }

        thrown.message shouldBe "Fant ikke deltakerliste med id ${expectedDeltakerliste.id}"
    }

    @Test
    fun `ny liste v2 gruppe - lagrer deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            antallPlasser = 5,
            tiltakstype = tiltakstype,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        val deltakerlistePayload = lagDeltakerlistePayload(arrangorInTest, deltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerlistePayload.id,
            value = objectMapper.writeValueAsString(deltakerlistePayload),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe deltakerliste
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            gjennomforingstype = GjennomforingType.Enkeltplass,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
            prisinformasjon = "100kr",
        )

        val deltakerlistePayload = lagEnkeltplassDeltakerlistePayload(arrangorInTest, deltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerlistePayload.id,
            value = objectMapper.writeValueAsString(deltakerlistePayload),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe deltakerliste.copy(
            navn = "Test tiltak ${deltakerliste.tiltakstype.tiltakskode}",
            status = deltakerliste.status,
            startDato = null,
            sluttDato = null,
            oppmoteSted = null,
        )
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype()
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            antallPlasser = 5,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerliste.id,
            value = objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, deltakerliste)),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe deltakerliste
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() = runTest {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            antallPlasser = 5,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerliste.id,
            value = objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe oppdatertDeltakerliste
    }

    @Test
    fun `consumeDeltakerliste - avbrutt - oppdaterer deltakerliste og avslutter deltakere`() = runTest {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            antallPlasser = 5,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(
            sluttDato = LocalDate.now(),
            status = GjennomforingStatusType.AVBRUTT,
        )

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerliste.id,
            value = objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe oppdatertDeltakerliste
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() = runTest {
        // Arrange
        val deltakerliste = lagDeltakerliste()
        TestRepository.insert(deltakerliste)

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerliste.id,
            value = null,
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeFailure()
    }

    @Test
    fun `consumeDeltakerliste - redusert sluttdato - oppdaterer deltakerliste og oppdaterer sluttdato pa deltakere`() = runTest {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            antallPlasser = 5,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )
        TestRepository.insert(deltakerliste)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        // Act
        deltakerlisteConsumer.consume(
            key = deltakerliste.id,
            value = objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).shouldBeSuccess() shouldBe oppdatertDeltakerliste
    }

    @Nested
    inner class AvsluttDeltakelserPaaDeltakerlisteTests {
        private val sistEndretAvNavEnhet = lagNavEnhet()
        private val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)

        @Test
        fun `avsluttDeltakelserPaaDeltakerliste - deltakerliste avlyst - setter riktig status og sluttarsak`() = runTest {
            // Arrange
            navEnhetRepository.upsert(sistEndretAvNavEnhet)
            navAnsattRepository.upsert(sistEndretAvNavAnsatt)

            val deltakerliste = lagDeltakerliste(
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVLYST,
            )
            TestRepository.insert(deltakerliste)

            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = deltakerliste,
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(deltaker, vedtak)

            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().minusDays(2),
                deltakerliste = deltakerliste,
            )
            val vedtak2 = lagVedtak(
                deltakerId = deltaker2.id,
                deltakerVedVedtak = deltaker2,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(deltaker2, vedtak2)

            every {
                deltakerProducerService.produce(any(), any(), any(), any(), any())
            } just Runs

            // Act
            deltakerlisteConsumer.avsluttDeltakelserPaaDeltakerliste(deltakerliste)

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakerRepository.get(deltaker2.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe null
                sluttdato shouldBe deltaker2.sluttdato
            }
        }
    }
}
