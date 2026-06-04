package no.nav.amt.deltaker.tiltaksansvarlig.endring

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeComparableWith
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

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhetInTest)
        navAnsattRepository.upsert(navAnsattInTest)
    }

    @Nested
    inner class OppdaterDeltakereTests {
        @Test
        fun `oppdaterDeltakere - sett på venteliste - upserter endring`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )

            TestRepository.insertAll(deltaker, deltaker2, innsokt, innsokt2)

            // Act
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2
            endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker shouldBeComparableWith deltaker.copy(
                status = deltaker.status.copy(type = DeltakerStatus.Type.VENTELISTE),
                startdato = null,
                sluttdato = null,
            )

            endredeDeltakere
                .first {
                    it.deltaker.id == deltaker2.id
                }.deltaker shouldBeComparableWith deltaker2.copy(
                status = deltaker2.status.copy(type = DeltakerStatus.Type.VENTELISTE),
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
        fun `oppdaterDeltakere - tildel plass feiler på upsert - ruller tilbake endringer på samme deltaker`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
            )
            val deltaker1Id = UUID.randomUUID()
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker1Id,
                opprettetAvEnhet = navEnhetInTest,
                opprettetAv = navAnsattInTest,
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                id = deltaker1Id,
                deltakerliste = deltakerliste,
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, deltaker2, innsokt, innsokt2, vedtak)

            // Act
            val endredeDeltakereResults = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakereResults.size shouldBe 2
            endredeDeltakereResults
                .first {
                    it.deltaker.id == deltaker.id
                }.deltaker shouldBeComparableWith deltaker.copy(
                status = deltaker.status.copy(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
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

            val ikkeEndretDeltakerResult = endredeDeltakereResults.first {
                it.deltaker.id == deltaker2.id
            }

            ikkeEndretDeltakerResult.deltaker shouldBeComparableWith deltaker2

            ikkeEndretDeltakerResult.isSuccess shouldBe false
            ikkeEndretDeltakerResult.exception shouldBe
                IllegalStateException("Deltaker ${deltaker2.id} mangler et vedtak som kan fattes")

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
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
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
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
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
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
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().minusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
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
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe null
                sluttdato shouldBe null
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
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
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltakerInsert = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2Insert = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltakerInsert,
                deltakerId = deltakerInsert.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )

            val deltakerIder = setOf(deltakerInsert.id, deltaker2Insert.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltakerInsert.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
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
            val deltakereResult = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            deltakereResult.size shouldBe 2
            deltakereResult.filter { it.isSuccess }.size shouldBe 1

            assertSoftly(deltakereResult.first { it.deltaker.id == deltakerInsert.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakereResult.first { it.deltaker.id == deltaker2Insert.id }.deltaker) {
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
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode =
                        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )

            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
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
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                erManueltDeltMedArrangor shouldBe true
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
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
    }

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
            val oppdateringResult = tiltaksansvarligService.giAvslag(
                deltakerId = deltaker.id,
                avslag = avslag,
                endretAv = navAnsatt.navIdent,
            )

            // Assert*
            val endringer = endringFraTiltakskoordinatorRepository.getForDeltaker(oppdateringResult.deltaker.id)
            endringer.size shouldBe 1
            (endringer.first().endring is EndringFraTiltakskoordinator.Avslag) shouldBe true

            assertSoftly(oppdateringResult.deltaker) {
                status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.KURS_FULLT
                startdato shouldBe null
                sluttdato shouldBe null
            }

            outboxService.assertProducedHendelse<HendelseType.Avslag>(oppdateringResult.deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(oppdateringResult.deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(oppdateringResult.deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                oppdateringResult.deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }
    }
}
