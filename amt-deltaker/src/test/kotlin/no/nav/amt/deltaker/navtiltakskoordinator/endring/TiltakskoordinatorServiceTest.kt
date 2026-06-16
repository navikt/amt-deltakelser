package no.nav.amt.deltaker.navtiltakskoordinator.endring

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertNotProduced
import no.nav.amt.deltaker.utils.assertNotProducedHendelse
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeComparableWith
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorServiceTest : IntegrationTestWithDbBase() {
    private val navEnhetInTest = TestData.lagNavEnhet(enhetsnummer = "0326")
    private val navAnsattInTest = TestData.lagNavAnsatt(navEnhetId = navEnhetInTest.id)
    override val deltakerLaaseService: DeltakerLaaseService = mockk()

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhetInTest)
        navAnsattRepository.upsert(navAnsattInTest)
        every { deltakerLaaseService.erLaastForEndringerForDeltakere(any(), any()) } answers {
            firstArg<Map<UUID, String>>().keys.associateWith { false }
        }
    }

    @Nested
    inner class OppdaterDeltakereTests {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(
                tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            ),
        )

        @Test
        fun `oppdaterDeltakere - sett på venteliste - upserter endring`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(deltakerliste = deltakerliste)
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )

            TestRepository.insertAll(deltaker, deltaker2, innsokt, innsokt2)

            // Act
            val endredeDeltakere = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltaker.deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2
            endredeDeltakere.forEach {
                it.isSuccess shouldBe true
                it.exception shouldBe null
            }
            endredeDeltakere.any { it.deltakerId == deltaker.id } shouldBe true
            val res = deltakerRepository.getMany(deltakerIder)
            res.size shouldBe 2
            res.first { it.id == deltaker.id } shouldBeComparableWith deltaker.copy(
                status = deltaker.status.copy(type = DeltakerStatus.Type.VENTELISTE),
                startdato = null,
                sluttdato = null,
            )
            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProducedHendelse<HendelseType.SettPaaVenteliste>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            // deltaker2
            outboxService.assertProduced<DeltakerKafkaPayload>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_V1_TOPIC,
            )
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - en deltaker er låst - upserter ikke endring`() = runTest {
            // Arrange
            val laastDeltaker = lagDeltaker(deltakerliste = deltakerliste)
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(laastDeltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = laastDeltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            every { deltakerLaaseService.erLaastForEndringerForDeltakere(any(), laastDeltaker.deltakerliste.id) } returns mapOf(
                laastDeltaker.id to true,
                deltaker2.id to false,
            )

            TestRepository.insertAll(laastDeltaker, deltaker2, innsokt, innsokt2)

            // Act
            val endredeDeltakere = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2
            val laastDeltakerResult = endredeDeltakere.first { it.deltakerId == laastDeltaker.id }

            laastDeltakerResult.isSuccess shouldBe false
            laastDeltakerResult.exception shouldBe IllegalStateException("Deltaker ${laastDeltaker.id} er låst for endringer")
            laastDeltakerResult.deltakerId shouldBe laastDeltaker.id

            endredeDeltakere
                .first {
                    it.deltakerId == deltaker2.id
                }.deltakerId shouldBe deltaker2.id

            val historikk1 = deltakerHistorikkService.getForDeltaker(laastDeltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 0

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertNotProducedHendelse<HendelseType.SettPaaVenteliste>(laastDeltaker.id)
            assertDeltakerNotProduced(laastDeltaker.id)
            // deltaker2
            assertDeltakerProduced(deltaker2.id)
        }

        @Test
        fun `oppdaterDeltakere - tildel plass feiler på upsert - ruller tilbake endringer på samme deltaker`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
            )
            val deltaker = lagDeltaker(
                id = UUID.randomUUID(),
                deltakerliste = deltakerliste,
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAvEnhet = navEnhetInTest,
                opprettetAv = navAnsattInTest,
            )
            val deltakerMedVedtak = deltaker.copy(
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            val deltakerUtenVedtak = lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltakerUtenVedtak.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltakerUtenVedtak.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltakerMedVedtak, deltakerUtenVedtak, innsokt, innsokt2, vedtak)

            // Act
            val endredeDeltakereResults = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakereResults.size shouldBe 2

            val suksessResult = endredeDeltakereResults.first { it.deltakerId == deltaker.id }
            suksessResult.isSuccess shouldBe true
            suksessResult.exception shouldBe null

            val feiletResult = endredeDeltakereResults.first { it.deltakerId == deltakerUtenVedtak.id }
            feiletResult.isSuccess shouldBe false
            feiletResult.exception!!.message shouldBe "Deltaker ${deltakerUtenVedtak.id} mangler et vedtak som kan fattes"
            val deltakerResult = deltakerRepository.getMany(deltakerIder)

            deltakerResult.first { it.id == deltaker.id } shouldBeComparableWith deltakerMedVedtak.copy(
                status = deltakerMedVedtak.status.copy(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
                vedtaksinformasjon = vedtak
                    .copy(
                        fattet = LocalDateTime.now(),
                        fattetAvNav = true,
                        sistEndret = LocalDateTime.now(),
                        sistEndretAvEnhet = vedtak.opprettetAvEnhet,
                    ).tilVedtaksInformasjon(),
            )

            val ikkeEndretDeltakerResult = deltakerResult.first {
                it.id == deltakerUtenVedtak.id
            }

            ikkeEndretDeltakerResult shouldBeComparableWith deltakerUtenVedtak

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltakerUtenVedtak.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 0

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - tildel plass - upserter endring, bruker deltakerliste sin start og sluttdato`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            // Act
            val endredeDeltakere = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            val deltakerResult = deltakerRepository.getMany(deltakerIder)
            assertSoftly(deltakerResult.first { it.id == deltaker.id }) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakerResult.first { it.id == deltaker2.id }) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltaker2.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - tildel plass - upserter endring, dato passert får start og sluttdato null`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().minusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            // Act
            val endredeDeltakere = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2
            val deltakerResult = deltakerRepository.getMany(deltakerIder)
            assertSoftly(deltakerResult.first { it.id == deltaker.id }) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe null
                sluttdato shouldBe null
            }

            assertSoftly(deltakerResult.first { it.id == deltaker2.id }) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe null
                sluttdato shouldBe null
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltaker2.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - tildel plass feiler på siste deltaker - ruller tilbake en deltaker`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltakerInsert = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2Insert = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltakerInsert,
                deltakerId = deltakerInsert.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )

            val deltakerIder = setOf(deltakerInsert.id, deltaker2Insert.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltakerInsert.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2Insert.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltakerInsert,
                deltaker2Insert,
                innsokt,
                innsokt2,
                vedtak,
            )

            // Act
            val deltakereResult = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            deltakereResult.size shouldBe 2
            deltakereResult.filter { it.isSuccess }.size shouldBe 1
            val deltakerResult = deltakerRepository.getMany(deltakerIder)
            assertSoftly(deltakerResult.first { it.id == deltakerInsert.id }) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakerResult.first { it.id == deltaker2Insert.id }) {
                status.type shouldBe deltaker2Insert.status.type
                startdato shouldBe deltaker2Insert.startdato
                sluttdato shouldBe deltaker2Insert.sluttdato
            }

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltakerInsert.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltakerInsert.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltakerInsert.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltakerInsert.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - del med arrangør - inserter endring og returnerer endret deltaker`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode =
                        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )

            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
            )

            // Act
            val endredeDeltakere = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2
            endredeDeltakere.forEach {
                it.isSuccess shouldBe true
                it.exception shouldBe null
            }
            val deltakerResult = deltakerRepository.getMany(deltakerIder)
            assertSoftly(deltakerResult.first { it.id == deltaker.id }) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                erManueltDeltMedArrangor shouldBe true
            }

            assertSoftly(deltakerResult.first { it.id == deltaker2.id }) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                erManueltDeltMedArrangor shouldBe true
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker2.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `oppdaterDeltakere - deltakere paa ulik deltakerliste - kaster AuthorizationException`() = runTest {
            // Arrange
            val laastDeltaker = lagDeltaker()
            val deltaker2 = lagDeltaker()
            val deltakerIder = setOf(laastDeltaker.id, deltaker2.id)

            TestRepository.insertAll(laastDeltaker, deltaker2)

            // Act
            shouldThrow<IllegalArgumentException> {
                tiltakskoordinatorService.oppdaterDeltakere(
                    gjennomforingId = deltakerliste.id,
                    deltakerIder = deltakerIder,
                    endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                    endretAvIdent = navAnsattInTest.navIdent,
                )
            }
        }

        @Test
        fun `oppdaterDeltakere - sett på venteliste allerede VENTELISTE - returnerer isSuccess false`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.VENTELISTE),
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act
            val result = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = setOf(deltaker.id),
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            result.size shouldBe 1
            result.first().isSuccess shouldBe false
            result.first().exception!!.message shouldBe "Ingen gyldig deltakerendring"

            val deltakerResult = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerResult.status.type shouldBe DeltakerStatus.Type.VENTELISTE

            assertDeltakerNotProduced(deltaker.id)
        }

        @Test
        fun `oppdaterDeltakere - del med arrangør - deltaker ikke SOKT_INN - returnerer isSuccess false`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.VENTELISTE),
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act
            val result = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = setOf(deltaker.id),
                endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            result.size shouldBe 1
            result.first().isSuccess shouldBe false
            result.first().exception!!.message shouldBe "Ingen gyldig deltakerendring"

            val deltakerResult = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerResult.erManueltDeltMedArrangor shouldBe false

            assertDeltakerNotProduced(deltaker.id)
        }

        @Test
        fun `oppdaterDeltakere - del med arrangør - allerede delt - returnerer isSuccess false`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                erManueltDeltMedArrangor = true,
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act
            val result = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = setOf(deltaker.id),
                endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            result.size shouldBe 1
            result.first().isSuccess shouldBe false
            result.first().exception!!.message shouldBe "Ingen gyldig deltakerendring"

            assertDeltakerNotProduced(deltaker.id)
        }

        @Test
        fun `oppdaterDeltakere - deltaker mangler aktiv oppfølgingsperiode - returnerer isSuccess false`() = runTest {
            // Arrange
            val navBrukerUtenOppfolging = TestData.lagNavBruker(
                oppfolgingsperioder = emptyList(),
            )
            val deltaker = lagDeltaker(
                navBruker = navBrukerUtenOppfolging,
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act
            val result = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = setOf(deltaker.id),
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            result.size shouldBe 1
            result.first().isSuccess shouldBe false
            result.first().exception!!.message shouldBe "Nav-bruker mangler aktiv oppfølgingsperiode"

            assertDeltakerNotProduced(deltaker.id)
        }

        @Test
        fun `oppdaterDeltakere - deltaker er FEILREGISTRERT - returnerer isSuccess false`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act
            val result = tiltakskoordinatorService.oppdaterDeltakere(
                gjennomforingId = deltakerliste.id,
                deltakerIder = setOf(deltaker.id),
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            result.size shouldBe 1
            result.first().isSuccess shouldBe false
            result.first().exception!!.message shouldBe "Ingen gyldig deltakerendring"

            assertDeltakerNotProduced(deltaker.id)
        }

        @Test
        fun `oppdaterDeltakere - tiltakskode ikke i tillatt sett - kaster IllegalArgumentException`() = runTest {
            // Arrange
            val ugyldigDeltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
            )
            val deltaker = lagDeltaker(deltakerliste = ugyldigDeltakerliste)
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                tiltakskoordinatorService.oppdaterDeltakere(
                    gjennomforingId = ugyldigDeltakerliste.id,
                    deltakerIder = setOf(deltaker.id),
                    endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                    endretAvIdent = navAnsattInTest.navIdent,
                )
            }
        }

        @Test
        fun `oppdaterDeltakere - tildel plass uten startdato på deltakerliste - kaster IllegalStateException`() = runTest {
            // Arrange
            val deltakerlisteUtenStartdato = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = null,
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerlisteUtenStartdato,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val innsokt = lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, innsokt, vedtak)

            // Act & Assert
            val exception = shouldThrow<IllegalStateException> {
                tiltakskoordinatorService.oppdaterDeltakere(
                    gjennomforingId = deltakerlisteUtenStartdato.id,
                    deltakerIder = setOf(deltaker.id),
                    endringsType = EndringFraTiltakskoordinator.TildelPlass,
                    endretAvIdent = navAnsattInTest.navIdent,
                )
            }
            exception.message shouldBe "Kursdeltaker mangler startdato"
        }
    }

    @Nested
    inner class Avslag {
        @Test
        fun `giAvslag - deltaker får riktig status`() = runTest {
            with(EndringFraTiltakskoordinatorCtx()) {
                // Arrange
                medInnsok()

                val avslag = EndringFraTiltakskoordinator.Avslag(
                    aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                        type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                        beskrivelse = null,
                    ),
                    begrunnelse = "Fordi...",
                )

                // Act
                val oppdateringResult = tiltakskoordinatorService.giAvslag(
                    gjennomforingId = deltakerliste.id,
                    deltakerId = deltaker.id,
                    avslag = avslag,
                    endretAv = navAnsatt.navIdent,
                )

                // Assert*
                oppdateringResult.isSuccess shouldBe true
                oppdateringResult.exception shouldBe null
                oppdateringResult.deltakerId shouldBe deltaker.id

                val endringer = endringFraTiltakskoordinatorRepository.getForDeltaker(oppdateringResult.deltakerId)
                endringer.size shouldBe 1
                (endringer.first().endring is EndringFraTiltakskoordinator.Avslag) shouldBe true

                val deltakerResult = deltakerRepository.get(oppdateringResult.deltakerId).getOrThrow()
                assertSoftly(deltakerResult) {
                    status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                    status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.KURS_FULLT
                    status.aarsak?.beskrivelse shouldBe null
                    startdato shouldBe null
                    sluttdato shouldBe null
                }

                outboxService.assertProducedHendelse<HendelseType.Avslag>(oppdateringResult.deltakerId)
                outboxService.assertProduced<DeltakerKafkaPayload>(oppdateringResult.deltakerId, Environment.DELTAKER_V2_TOPIC)
                outboxService.assertProduced<DeltakerV1Dto>(oppdateringResult.deltakerId, Environment.DELTAKER_V1_TOPIC)
                outboxService.assertProduced<DeltakerEksternV1Dto>(
                    oppdateringResult.deltakerId,
                    Environment.DELTAKER_EKSTERN_V1_TOPIC,
                )
            }
        }

        @Test
        fun `giAvslag - aarsak ANNET med beskrivelse - lagrer beskrivelse`() = runTest {
            with(EndringFraTiltakskoordinatorCtx()) {
                // Arrange
                medInnsok()

                val avslag = EndringFraTiltakskoordinator.Avslag(
                    aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                        type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.ANNET,
                        beskrivelse = "Spesifikk grunn",
                    ),
                    begrunnelse = "Utdypende begrunnelse",
                )

                // Act
                val oppdateringResult = tiltakskoordinatorService.giAvslag(
                    gjennomforingId = deltakerliste.id,
                    deltakerId = deltaker.id,
                    avslag = avslag,
                    endretAv = navAnsatt.navIdent,
                )

                // Assert
                oppdateringResult.isSuccess shouldBe true

                val deltakerResult = deltakerRepository.get(oppdateringResult.deltakerId).getOrThrow()
                assertSoftly(deltakerResult) {
                    status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                    status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.ANNET
                    status.aarsak?.beskrivelse shouldBe "Spesifikk grunn"
                    startdato shouldBe null
                    sluttdato shouldBe null
                }

                val endringer = endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id)
                val lagretAvslag = endringer.first().endring as EndringFraTiltakskoordinator.Avslag
                lagretAvslag.aarsak.beskrivelse shouldBe "Spesifikk grunn"
                lagretAvslag.begrunnelse shouldBe "Utdypende begrunnelse"
            }
        }

        @Test
        fun `giAvslag - deltaker har ugyldig status DELTAR - kaster exception`() = runTest {
            with(EndringFraTiltakskoordinatorCtx()) {
                // Arrange
                medStatusDeltar()
                medInnsok()

                val avslag = EndringFraTiltakskoordinator.Avslag(
                    aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                        type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                        beskrivelse = null,
                    ),
                    begrunnelse = null,
                )

                // Act & Assert
                shouldThrow<IllegalStateException> {
                    tiltakskoordinatorService.giAvslag(
                        gjennomforingId = deltakerliste.id,
                        deltakerId = deltaker.id,
                        avslag = avslag,
                        endretAv = navAnsatt.navIdent,
                    )
                }

                val deltakerResult = deltakerRepository.get(deltaker.id).getOrThrow()
                deltakerResult.status.type shouldBe DeltakerStatus.Type.DELTAR

                outboxService.assertNotProducedHendelse<HendelseType.Avslag>(deltaker.id)
                assertDeltakerNotProduced(deltaker.id)
            }
        }

        @Test
        fun `giAvslag - deltaker er låst for endringer - kaster exception`() = runTest {
            with(EndringFraTiltakskoordinatorCtx()) {
                // Arrange
                medInnsok()
                every { deltakerLaaseService.erLaastForEndringerForDeltakere(any(), deltakerliste.id) } returns
                    mapOf(deltaker.id to true)

                val avslag = EndringFraTiltakskoordinator.Avslag(
                    aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                        type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                        beskrivelse = null,
                    ),
                    begrunnelse = null,
                )

                // Act & Assert
                shouldThrow<IllegalStateException> {
                    tiltakskoordinatorService.giAvslag(
                        gjennomforingId = deltakerliste.id,
                        deltakerId = deltaker.id,
                        avslag = avslag,
                        endretAv = navAnsatt.navIdent,
                    )
                }

                val deltakerResult = deltakerRepository.get(deltaker.id).getOrThrow()
                deltakerResult.status.type shouldBe DeltakerStatus.Type.SOKT_INN

                outboxService.assertNotProducedHendelse<HendelseType.Avslag>(deltaker.id)
                assertDeltakerNotProduced(deltaker.id)
            }
        }
    }

    fun assertDeltakerNotProduced(deltakerId: UUID) {
        outboxService.assertNotProduced<DeltakerKafkaPayload>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_V2_TOPIC,
        )
        outboxService.assertNotProduced<DeltakerV1Dto>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_V1_TOPIC,
        )
        outboxService.assertNotProduced<DeltakerEksternV1Dto>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
        )
    }

    fun assertDeltakerProduced(deltakerId: UUID) {
        outboxService.assertProduced<DeltakerKafkaPayload>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_V2_TOPIC,
        )
        outboxService.assertProduced<DeltakerV1Dto>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_V1_TOPIC,
        )
        outboxService.assertProduced<DeltakerEksternV1Dto>(
            expectedKey = deltakerId,
            expectedTopic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
        )
    }
}
