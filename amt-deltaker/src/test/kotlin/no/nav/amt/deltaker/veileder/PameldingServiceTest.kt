package no.nav.amt.deltaker.veileder

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedDirekteVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedTrengerGodkjenning
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.PameldingService.Companion.getOppdatertStatus
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PameldingServiceTest : IntegrationTestWithDbBase() {
    val sistEndretAvNavEnhet = lagNavEnhet()
    val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)
    val tiltak = TestData.lagTiltakstype(Tiltakskode.ARBEIDSMARKEDSOPPLAERING)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)
        tiltakRepository.upsert(tiltak)
    }

    @Nested
    inner class OpprettDeltakerTests {
        @Test
        fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`() = runTest {
            // Arrange
            val expectedDeltaker = lagDeltaker(
                sluttdato = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(expectedDeltaker)

            // Act
            val actualDeltaker = kladdService.opprettKladd(
                expectedDeltaker.deltakerliste.id,
                expectedDeltaker.navBruker.personident,
            )

            // Assert
            actualDeltaker.id shouldBe expectedDeltaker.id
        }

        @Test
        fun `opprettKladd - deltaker finnes ikke - oppretter ny deltaker`() = runTest {
            // Arrange
            val arrangor = lagArrangor()
            val deltakerListe = lagDeltakerliste(arrangor = arrangor)
            TestRepository.insert(deltakerListe)

            val navBruker = lagNavBruker(
                navVeilederId = sistEndretAvNavAnsatt.id,
                navEnhetId = sistEndretAvNavEnhet.id,
            )

            coEvery { personServiceClient.hentNavBruker(navBruker.personident) } returns navBruker

            // Act
            val deltaker = kladdService.opprettKladd(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            // Assert
            assertSoftly(deltaker) {
                id shouldBe deltakerRepository
                    .getFlereForPerson(
                        personIdent = navBruker.personident,
                        deltakerlisteId = deltakerListe.id,
                    ).first()
                    .id

                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold
                    .shouldNotBeNull()
                    .ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }

        @Test
        fun `opprettKladd - ARR, deltaker har situasjonsbetinget inns og sykmeldt - oppretter ny deltaker`() = runTest {
            // Arrange
            val tiltakstype = TestData.lagTiltakstype(
                tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING,
                innsatsgrupper = setOf(Innsatsgruppe.VARIG_TILPASSET_INNSATS, Innsatsgruppe.SPESIELT_TILPASSET_INNSATS),
            )
            val arrangor = lagArrangor()
            val deltakerListe = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            TestRepository.insert(deltakerListe)

            val navBruker = lagNavBruker(
                navVeilederId = sistEndretAvNavAnsatt.id,
                navEnhetId = sistEndretAvNavEnhet.id,
                innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS,
            )

            coEvery { personServiceClient.hentNavBruker(navBruker.personident) } returns navBruker

            // Act
            val deltaker = kladdService.opprettKladd(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            // Assert
            assertSoftly(deltaker) {
                id shouldBe deltakerRepository
                    .getFlereForPerson(
                        personIdent = navBruker.personident,
                        deltakerlisteId = deltakerListe.id,
                    ).first()
                    .id
                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold
                    .shouldNotBeNull()
                    .ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }

        @Test
        fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() = runTest {
            // Arrange
            val personIdent = randomIdent()

            coEvery {
                personServiceClient.hentNavBruker(any())
            } throws NoSuchElementException("NavBruker ikke funnet")

            // Act & Assert
            shouldThrow<NoSuchElementException> {
                kladdService.opprettKladd(
                    deltakerListeId = UUID.randomUUID(),
                    personIdent = personIdent,
                )
            }
        }

        @Test
        fun `opprettKladd - deltaker finnes men har sluttet - oppretter ny deltaker`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                sluttdato = LocalDate.now().minusMonths(3),
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)

            // Act
            val nyDeltaker = kladdService.opprettKladd(
                deltaker.deltakerliste.id,
                deltaker.navBruker.personident,
            )

            // Assert
            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }

    @Nested
    inner class UpsertUtkastTests {
        @Test
        fun `upsertUtkast - deltaker finnes - oppdaterer deltaker og oppretter vedtak`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                vedtaksinformasjon = null,
            )
            TestRepository.insert(deltaker)

            val utkastRequest = UtkastRequest(
                deltakelsesinnhold = Deltakelsesinnhold(
                    ledetekst = "utkastledetekst",
                    innhold = listOf(Innhold("Tekst", "kode", true, null)),
                ),
                bakgrunnsinformasjon = "Bakgrunn",
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                endretAv = sistEndretAvNavAnsatt.navIdent,
                endretAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
                godkjentAvNav = false,
            )

            // Act
            pameldingService.upsertUtkast(
                deltakerId = deltaker.id,
                utkast = utkastRequest,
            )

            // Assert
            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            assertSoftly(vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()) {
                fattet shouldBe null
                fattetAvNav shouldBe false
                it.sistEndretAv shouldBe sistEndretAvNavAnsatt.id
                it.sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
            }

            outboxService.assertProducedHendelse<HendelseType.OpprettUtkast>(deltaker.id)
        }

        @Test
        fun `deltaker med direkte vedtak, godkjent av Nav - oppdaterer deltaker og oppretter fattet vedtak`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerliste(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK),
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                vedtaksinformasjon = null,
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val utkastRequest = UtkastRequest(
                deltakelsesinnhold = Deltakelsesinnhold("test", listOf(Innhold("Tekst", "kode", true, null))),
                bakgrunnsinformasjon = "Bakgrunn",
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                endretAv = sistEndretAvNavAnsatt.navIdent,
                endretAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
                godkjentAvNav = true,
            )

            // Act
            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            // Assert
            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            assertSoftly(vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()) {
                fattet shouldNotBe null
                fattetAvNav shouldBe true
                it.sistEndretAv shouldBe sistEndretAvNavAnsatt.id
                it.sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
            }

            innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeFailure()

            outboxService.assertProducedHendelse<HendelseType.NavGodkjennUtkast>(deltaker.id)
        }

        @Test
        fun `upsertUtkast - deltaker med trenger godkjenning, godkjent av Nav - oppdaterer deltaker`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerliste(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING),
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                vedtaksinformasjon = null,
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val utkastRequest = UtkastRequest(
                deltakelsesinnhold = Deltakelsesinnhold(
                    ledetekst = "test",
                    innhold = listOf(Innhold("Tekst", "kode", true, null)),
                ),
                bakgrunnsinformasjon = "Bakgrunn",
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                endretAv = sistEndretAvNavAnsatt.navIdent,
                endretAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
                godkjentAvNav = true,
            )

            // Act
            pameldingService.upsertUtkast(deltakerId = deltaker.id, utkast = utkastRequest)

            // Assert
            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.SOKT_INN
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            val vedtak = vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()
            vedtak.fattet shouldBe null
            vedtak.fattetAvNav shouldBe false

            val innsok = innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeSuccess()
            assertSoftly(innsok) {
                utkastGodkjentAvNav shouldBe true
                utkastDelt shouldBe null
                innsokt shouldBeCloseTo LocalDateTime.now()
            }

            outboxService.assertProducedHendelse<HendelseType.NavGodkjennUtkast>(deltaker.id)
        }
    }

    @Nested
    inner class AvbrytUtkastTests {
        @Test
        fun `avbrytUtkast - utkast finnes - oppdaterer deltaker og vedtak`() = runTest {
            // Arrange
            val sistEndretAvNavAnsatt = lagNavAnsatt()
            val sistEndretAvNavEnhet = lagNavEnhet()
            TestRepository.insertAll(sistEndretAvNavAnsatt, sistEndretAvNavEnhet)

            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
                fattet = null,
                gyldigTil = null,
            )
            TestRepository.insert(
                deltaker = deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon()),
                vedtak = vedtak,
            )

            val avbrytUtkastRequest = AvbrytUtkastRequest(
                avbruttAv = sistEndretAvNavAnsatt.navIdent,
                avbruttAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
            )

            // Act
            pameldingService.avbrytUtkast(deltaker.id, avbrytUtkastRequest)

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
                vedtaksinformasjon shouldBe null
            }

            assertSoftly(vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()) {
                fattet shouldBe null
                fattetAvNav shouldBe false
                gyldigTil shouldNotBe null
                sistEndretAv shouldBe sistEndretAvNavAnsatt.id
                sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
            }

            outboxService.assertProducedHendelse<HendelseType.AvbrytUtkast>(deltaker.id)
        }
    }

    @Nested
    inner class InnbyggerGodkjennUtkastTests {
        private fun assertProduced(deltakerId: UUID) {
            outboxService.assertProducedHendelse<HendelseType.InnbyggerGodkjennUtkast>(deltakerId)

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

        @Test
        fun `deltaker med lopende oppstart - vedtak fattes og ny status er godkjent utkast`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedDirekteVedtak(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            // Act
            pameldingService.innbyggerGodkjennUtkast(deltaker.id)

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                vedtaksinformasjon.shouldNotBeNull()
                vedtaksinformasjon.fattet shouldBeCloseTo LocalDateTime.now()
            }

            innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeFailure()

            assertProduced(deltaker.id)
        }

        @Test
        fun `innbyggerGodkjennUtkast - deltaker med felles oppstart - vedtak fattes ikke ny status er sokt inn`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedTrengerGodkjenning(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            // Act
            pameldingService.innbyggerGodkjennUtkast(deltaker.id)

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                vedtaksinformasjon.shouldNotBeNull()
                vedtaksinformasjon.fattet shouldBe null
            }

            val innsok = innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeSuccess()
            assertSoftly(innsok) {
                utkastGodkjentAvNav shouldBe false
                utkastDelt shouldNotBe null
                innsokt shouldBeCloseTo LocalDateTime.now()
            }

            assertProduced(deltaker.id)
        }

        @Test
        fun `innbyggerGodkjennUtkast - enkeltplass - vedtak fattes og gjennomforing publiseres`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedTrengerGodkjenning()
                    .copy(
                        gjennomforingstype = GjennomforingType.Enkeltplass,
                        prisinformasjon = "Dette tiltaket koster 100 kr/mnd",
                    ),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker, fattet = null)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            // Act
            pameldingService.innbyggerGodkjennUtkast(deltaker.id)

            // Assert
            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                vedtaksinformasjon.shouldNotBeNull()
                vedtaksinformasjon.fattetAvNav shouldBe false
                vedtaksinformasjon.fattet shouldBe null
            }

            outboxService.assertProduced<GjennomforingRequestPayload>(
                expectedKey = deltaker.deltakerliste.id,
                expectedTopic = Environment.GJENNOMFORING_REQUEST_TOPIC,
            )
        }

        @Test
        fun `innbyggerGodkjennUtkast - vedtak kunne ikke fattes - upserter ikke`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedDirekteVedtak(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker, fattet = LocalDateTime.now())
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            // Act & Assert
            val thrown = shouldThrow<IllegalArgumentException> {
                pameldingService.innbyggerGodkjennUtkast(deltaker.id)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har allerede et fattet vedtak"

            val ikkeOppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            ikkeOppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
        }
    }

    @Nested
    inner class GetOppdatertStatusTests {
        @Test
        fun `getOppdatertStatus() - pameldingstype TRENGER_GODKJENNING - status SOKT_INN`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
            )
            val deltakerStatusKladd = lagDeltakerStatus(DeltakerStatus.Type.KLADD)
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = deltakerStatusKladd,
            )

            // Act
            val deltakerStatus = getOppdatertStatus(
                opprinneligDeltaker = deltaker,
                godkjentAvNav = true,
            )

            // Assert
            deltakerStatus.type shouldBe DeltakerStatus.Type.SOKT_INN
        }

        @Test
        fun `getOppdatertStatus() - pameldingstype DIREKTE_VEDTAK - status VENTER_PA_OPPSTART`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
            )
            val deltakerStatusKladd = lagDeltakerStatus(DeltakerStatus.Type.KLADD)
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = deltakerStatusKladd,
            )

            // Act
            val deltakerStatus = getOppdatertStatus(
                opprinneligDeltaker = deltaker,
                godkjentAvNav = true,
            )

            // Assert
            deltakerStatus.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }
    }
}
