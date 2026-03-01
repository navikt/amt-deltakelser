package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.OppfolgingstilfellePersonResponse
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.PameldingService.Companion.getOppdatertStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProduced
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerEksternV1
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerV1
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedDirekteVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedTrengerGodkjenning
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.TestOutboxEnvironment
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFailsWith

class PameldingServiceTest {
    val sistEndretAvNavEnhet = lagNavEnhet()
    val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)

        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalProdusereTilDeltakerEksternTopic() } returns true
    }

    @Nested
    inner class OpprettDeltakerTests {
        @Test
        fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`() = runTest {
            val expectedDeltaker = lagDeltaker(
                sluttdato = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(expectedDeltaker)

            val actualDeltaker = pameldingService.opprettDeltaker(
                expectedDeltaker.deltakerliste.id,
                expectedDeltaker.navBruker.personident,
            )

            actualDeltaker.id shouldBe expectedDeltaker.id
        }

        @Test
        fun `opprettKladd - deltaker finnes ikke - oppretter ny deltaker`() = runTest {
            val arrangor = lagArrangor()
            val deltakerListe = lagDeltakerliste(arrangor = arrangor)
            val navBruker = lagNavBruker(
                navVeilederId = sistEndretAvNavAnsatt.id,
                navEnhetId = sistEndretAvNavEnhet.id,
            )

            mockResponses(sistEndretAvNavEnhet, sistEndretAvNavAnsatt, navBruker)
            TestRepository.insert(deltakerListe)

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltakerRepository.getFlereForPerson(navBruker.personident, deltakerListe.id).first().id
                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold!!.ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }

        @Test
        fun `opprettKladd - ARR, deltaker har situasjonsbetinget inns og sykmeldt - oppretter ny deltaker`() = runTest {
            val tiltakstype = TestData.lagTiltakstype(
                tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING,
                innsatsgrupper = setOf(Innsatsgruppe.VARIG_TILPASSET_INNSATS, Innsatsgruppe.SPESIELT_TILPASSET_INNSATS),
            )
            val arrangor = lagArrangor()
            val deltakerListe = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            val navBruker = lagNavBruker(
                navVeilederId = sistEndretAvNavAnsatt.id,
                navEnhetId = sistEndretAvNavEnhet.id,
                innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS,
            )

            mockResponses(sistEndretAvNavEnhet, sistEndretAvNavAnsatt, navBruker)
            MockResponseHandler.addOppfolgingstilfelleRespons(
                OppfolgingstilfellePersonResponse(
                    listOf(
                        OppfolgingstilfelleDTO(
                            arbeidstakerAtTilfelleEnd = true,
                            start = LocalDate.now().minusMonths(3),
                            end = LocalDate.now().plusDays(1),
                        ),
                    ),
                ),
            )
            TestRepository.insert(deltakerListe)

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltakerRepository.getFlereForPerson(navBruker.personident, deltakerListe.id).first().id
                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold!!.ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }

        @Test
        fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() = runTest {
            val personIdent = TestData.randomIdent()

            assertFailsWith<NoSuchElementException> {
                pameldingService.opprettDeltaker(UUID.randomUUID(), personIdent)
            }
        }

        @Test
        fun `opprettKladd - deltaker finnes men har sluttet - oppretter ny deltaker`() = runTest {
            val deltaker = lagDeltaker(
                sluttdato = LocalDate.now().minusMonths(3),
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)

            val nyDeltaker =
                pameldingService.opprettDeltaker(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                )

            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }

    @Nested
    inner class UpsertUtkastTests {
        @Test
        fun `upsertUtkast - deltaker finnes - oppdaterer deltaker og oppretter vedtak`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                vedtaksinformasjon = null,
            )
            TestRepository.insert(deltaker)

            val utkastRequest = UtkastRequest(
                deltakelsesinnhold = Deltakelsesinnhold("utkastledetekst", listOf(Innhold("Tekst", "kode", true, null))),
                bakgrunnsinformasjon = "Bakgrunn",
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                endretAv = sistEndretAvNavAnsatt.navIdent,
                endretAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
                godkjentAvNav = false,
            )

            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            assertSoftly(vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()) {
                fattet shouldBe null
                fattetAvNav shouldBe false
                it.sistEndretAv shouldBe sistEndretAvNavAnsatt.id
                it.sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
            }

            assertProducedHendelse(deltaker.id, HendelseType.OpprettUtkast::class)
        }

        @Test
        fun `upsertUtkast - deltaker med direkte vedtak, godkjent av Nav - oppdaterer deltaker og oppretter fattet vedtak`() {
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerliste(
                    pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
                ),
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

            runTest {
                pameldingService.upsertUtkast(deltaker.id, utkastRequest)

                val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
                deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                deltakerFraDb.vedtaksinformasjon shouldNotBe null

                assertSoftly(vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()) {
                    fattet shouldNotBe null
                    fattetAvNav shouldBe true
                    it.sistEndretAv shouldBe sistEndretAvNavAnsatt.id
                    it.sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
                }

                innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).isFailure shouldBe true

                assertProducedHendelse(deltaker.id, HendelseType.NavGodkjennUtkast::class)
            }
        }

        @Test
        fun `upsertUtkast - deltaker med trenger godkjenning, godkjent av Nav - oppdaterer deltaker`() {
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerliste(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING),
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

            runTest {
                pameldingService.upsertUtkast(deltaker.id, utkastRequest)

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

                assertProducedHendelse(deltaker.id, HendelseType.NavGodkjennUtkast::class)
            }
        }
    }

    @Test
    fun `avbrytUtkast - utkast finnes - oppdaterer deltaker og vedtak`() {
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

        runTest {
            pameldingService.avbrytUtkast(deltaker.id, avbrytUtkastRequest)

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

            assertProducedHendelse(deltaker.id, HendelseType.AvbrytUtkast::class)
        }
    }

    @Nested
    inner class InnbyggerGodkjennUtkastTests {
        @Test
        fun `innbyggerGodkjennUtkast - deltaker med lopende oppstart - vedtak fattes og ny status er godkjent utkast`() = runTest {
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedDirekteVedtak(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            pameldingService.innbyggerGodkjennUtkast(deltaker.id)

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART

            oppdatertDeltaker.vedtaksinformasjon.shouldNotBeNull()
            oppdatertDeltaker.vedtaksinformasjon.fattet shouldBeCloseTo LocalDateTime.now()

            innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeFailure()
        }

        @Test
        fun `innbyggerGodkjennUtkast - deltaker med felles oppstart - vedtak fattes ikke ny status er sokt inn`() = runTest {
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedTrengerGodkjenning(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            pameldingService.innbyggerGodkjennUtkast(deltaker.id)

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
            assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.SOKT_INN

            oppdatertDeltaker.vedtaksinformasjon.shouldNotBeNull()
            oppdatertDeltaker.vedtaksinformasjon.fattet shouldBe null

            val innsok = innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).shouldBeSuccess()
            assertSoftly(innsok) {
                utkastGodkjentAvNav shouldBe false
                utkastDelt shouldNotBe null
                innsokt shouldBeCloseTo LocalDateTime.now()
            }
        }

        @Test
        fun `innbyggerGodkjennUtkast - vedtak kunne ikke fattes - upserter ikke`() {
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerlisteMedDirekteVedtak(),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker, fattet = LocalDateTime.now())
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
            TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

            val thrown = shouldThrow<IllegalArgumentException> {
                runTest {
                    pameldingService.innbyggerGodkjennUtkast(deltaker.id)
                }
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
            val deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
            )
            val deltakerStatusKladd = lagDeltakerStatus(DeltakerStatus.Type.KLADD)
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = deltakerStatusKladd,
            )

            val deltakerStatus = getOppdatertStatus(deltaker, true)

            deltakerStatus.type shouldBe DeltakerStatus.Type.SOKT_INN
        }

        @Test
        fun `getOppdatertStatus() - pameldingstype DIREKTE_VEDTAK - status VENTER_PA_OPPSTART`() {
            val deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
            )
            val deltakerStatusKladd = lagDeltakerStatus(DeltakerStatus.Type.KLADD)
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = deltakerStatusKladd,
            )

            val deltakerStatus = getOppdatertStatus(deltaker, true)

            deltakerStatus.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }
    }

    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, mockPersonServiceClient())

    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(navAnsattRepository, mockPersonServiceClient(), navEnhetService)

    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val forslagRepository = ForslagRepository()
    private val deltakerRepository = DeltakerRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val vedtakRepository = VedtakRepository()
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
    private val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
    private val endringFraTiltaksKoordinatorRepository = EndringFraTiltakskoordinatorRepository()
    private val vurderingRepository = VurderingRepository()

    private val deltakerHistorikkService =
        DeltakerHistorikkService(
            deltakerEndringRepository,
            vedtakRepository,
            forslagRepository,
            endringFraArrangorRepository,
            importertFraArenaRepository,
            innsokPaaFellesOppstartRepository,
            endringFraTiltaksKoordinatorRepository,
            vurderingRepository,
        )

    private val unleashToggle = mockk<CommonUnleashToggle>(relaxed = true)

    private val hendelseService = HendelseService(
        HendelseProducer(TestOutboxEnvironment.outboxService),
        navAnsattRepository,
        navAnsattService,
        navEnhetRepository,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
        VurderingService(VurderingRepository()),
        unleashToggle,
    )

    private val vedtakService = VedtakService(vedtakRepository)
    private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, mockk())
    private val deltakerKafkaPayloadBuilder =
        DeltakerKafkaPayloadBuilder(navAnsattRepository, navEnhetRepository, deltakerHistorikkService, VurderingRepository())

    private val deltakerProducer = DeltakerProducer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerV1Producer = DeltakerV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerEksternV1Producer =
        DeltakerEksternV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)

    private val deltakerProducerService =
        DeltakerProducerService(deltakerKafkaPayloadBuilder, deltakerProducer, deltakerV1Producer, deltakerEksternV1Producer, unleashToggle)

    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerProducerService = deltakerProducerService,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = DeltakerEndringService(
            deltakerEndringRepository,
            navAnsattRepository,
            navEnhetRepository,
            hendelseService,
            forslagService,
            deltakerHistorikkService,
        ),
        vedtakRepository = vedtakRepository,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        navAnsattService = navAnsattService,
        endringFraTiltakskoordinatorRepository = endringFraTiltaksKoordinatorRepository,
        navEnhetService = navEnhetService,
        forslagRepository = forslagRepository,
    )

    private val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        deltakerListeRepository = DeltakerlisteRepository(),
        navBrukerService = NavBrukerService(
            NavBrukerRepository(),
            mockPersonServiceClient(),
            navEnhetService,
            navAnsattService,
        ),
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun mockResponses(
            navEnhet: NavEnhet,
            navAnsatt: NavAnsatt,
            navBruker: NavBruker,
        ) {
            navAnsatt.navEnhetId?.let { MockResponseHandler.addNavEnhetGetResponse(lagNavEnhet(it)) }
            MockResponseHandler.addNavEnhetResponse(navEnhet)
            MockResponseHandler.addNavAnsattResponse(navAnsatt)
            MockResponseHandler.addNavBrukerResponse(navBruker)
        }
    }
}
