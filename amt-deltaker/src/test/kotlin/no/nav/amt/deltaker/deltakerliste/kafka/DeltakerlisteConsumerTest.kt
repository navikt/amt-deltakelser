package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
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
    private val arrangorInTest = lagArrangor()

    @BeforeEach
    fun setupMocks() {
        every { unleashToggle.skalLeseGjennomforing(any<String>()) } returns true
    }

    @Nested
    inner class AvgrensSluttdatoerTilTests {
        @Test
        fun `avgrensSluttdatoerTil - deltaker har senere sluttdato enn deltakerliste - deltakers sluttdato endres`() = runTest {
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

            deltakerlisteConsumer.avgrensSluttdatoerTil(deltakerliste)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.sluttdato shouldBe deltakerliste.sluttDato
        }

        @Test
        fun `avgrensSluttdatoerTil - deltaker har tidligere sluttdato enn deltakerliste - deltakers sluttdato endres ikke`() = runTest {
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

            deltakerlisteConsumer.avgrensSluttdatoerTil(deltakerliste)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.sluttdato shouldNotBe deltakerliste.sluttDato
        }
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
                deltakerlisteConsumer.consume(
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
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        tiltakstypeRepository.upsert(tiltakstype)

        val expectedDeltakerliste = lagDeltakerliste(arrangor = arrangorInTest, tiltakstype = tiltakstype)

        val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = lagDeltakerlistePayload(arrangorInTest, expectedDeltakerliste).copy(
            arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
        )

        every { unleashToggle.skalLeseGjennomforing(tiltakstype.tiltakskode.name) } returns false
        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        runTest {
            deltakerlisteConsumer.consume(
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
    fun `ny liste v2 gruppe - lagrer deltakerliste`() = runTest {
        // Arrange
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

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() = runTest {
        // Arrange
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

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            deltakerlistePayload.id,
            objectMapper.writeValueAsString(deltakerlistePayload),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste.copy(
            navn = "Test tiltak ${deltakerliste.tiltakstype.tiltakskode}",
            status = null,
            startDato = null,
            sluttDato = null,
            oppstart = null,
            oppmoteSted = null,
        )
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() = runTest {
        // Arrange
        val tiltakstype = lagTiltakstype()
        tiltakstypeRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstype,
            pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        coEvery { arrangorClient.hentArrangor(arrangorInTest.organisasjonsnummer) } returns lagArrangorResponse(arrangorInTest)

        // Act
        deltakerlisteConsumer.consume(
            deltakerliste.id,
            objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, deltakerliste)),
        )

        // Assert
        deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
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
            deltakerlisteConsumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }
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
            deltakerlisteConsumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() {
        val deltakerliste = lagDeltakerliste()

        TestRepository.insert(deltakerliste)

        runTest {
            deltakerlisteConsumer.consume(deltakerliste.id, null)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
        }
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
            deltakerlisteConsumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangorInTest, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }
    }

    // FLYTTET

    @Nested
    inner class AvsluttDeltakelserPaaDeltakerlisteTests {
        private val sistEndretAvNavEnhet = lagNavEnhet()
        private val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)

        @Test
        fun `avsluttDeltakelserPaaDeltakerliste - deltakerliste avlyst - setter riktig status og sluttarsak`() {
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

            runTest {
                // Act
                deltakerlisteConsumer.avsluttDeltakelserPaaDeltakerliste(deltakerliste)

                // Assert
                assertSoftly(deltakerRepository.get(deltaker.id).getOrThrow()) {
                    status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                    status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
                    sluttdato shouldBe deltakerliste.sluttDato
                }

                assertSoftly(deltakerRepository.get(deltaker2.id).getOrThrow()) {
                    status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                    status.aarsak?.type shouldBe null
                    sluttdato shouldBe deltaker2.sluttdato
                }
            }
        }
    }
}
