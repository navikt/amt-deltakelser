package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.KladdService.Companion.lagEnkeltplassUpdateDbo
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime

class EnkeltplassServiceIntegrationTest {
    val sistEndretAvNavEnhet = lagNavEnhet()
    val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)
    val tiltak = TestData.lagTiltakstype(Tiltakskode.ARBEIDSMARKEDSOPPLAERING)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)

        TiltakstypeRepository().upsert(tiltak)
    }

    @Nested
    inner class EnkeltplassTests {
        val navBruker = lagNavBruker(
            navVeilederId = sistEndretAvNavAnsatt.id,
            navEnhetId = sistEndretAvNavEnhet.id,
        )

        @BeforeEach
        fun beforeEach() {
            mockResponses(sistEndretAvNavEnhet, sistEndretAvNavAnsatt, navBruker)
        }

        @Test
        fun `opprettKladd - returnerer ny deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltaker.id
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                vedtaksinformasjon shouldBe null
                sistEndret shouldBeCloseTo LocalDateTime.now()
                kilde shouldBe Kilde.KOMET
                erManueltDeltMedArrangor shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(deltaker.status) {
                type shouldBe DeltakerStatus.Type.KLADD
            }

            assertSoftly(deltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltak
                navn shouldBe tiltak.navn
                startDato shouldBe null
                sluttDato shouldBe null
                oppstart shouldBe null
                apentForPamelding shouldBe false
                oppmoteSted shouldBe null
                arrangor shouldBe null
                pameldingstype shouldBe null
                status shouldBe GjennomforingStatusType.KLADD
            }
        }

        @Test
        fun `opprettKladd - det finnes allerede kladd på samme enkeltplass tiltakstype - returnerer ny deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )

            val deltaker2 = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )
            deltaker2.id shouldBe deltaker.id
        }

        @Test
        fun `oppdaterKladd - returnerer ny deltakerId`() = runTest {
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )
            val deltakerExpected = lagEnkeltplassUpdateDbo(
                deltakerId = deltakerInserted.id,
                tiltakstype = deltakerInserted.deltakerliste.tiltakstype,
                startdato = LocalDate.now().plusDays(1),
                sluttdato = LocalDate.now().plusDays(2),
                beskrivelse = "Beskrivelse",
            )
            val prisinfoExpected = "Prisinfo"
            val oppdatertDeltaker = enkeltplassService.oppdaterKladd(
                deltakerId = deltakerExpected.id,
                startdato = deltakerExpected.startdato,
                sluttdato = deltakerExpected.sluttdato,
                beskrivelse = deltakerExpected.deltakelsesinnhold
                    ?.innhold
                    ?.first()
                    ?.beskrivelse,
                prisinformasjon = prisinfoExpected,
            )

            oppdatertDeltaker shouldNotBe null

            assertSoftly(oppdatertDeltaker) {
                id shouldBe deltakerExpected.id
                startdato shouldBe deltakerExpected.startdato
                sluttdato shouldBe deltakerExpected.sluttdato
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                vedtaksinformasjon shouldBe null
                sistEndret shouldBeCloseTo LocalDateTime.now()
                kilde shouldBe Kilde.KOMET
                erManueltDeltMedArrangor shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.status) {
                type shouldBe DeltakerStatus.Type.KLADD
            }

            assertSoftly(oppdatertDeltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltak
                navn shouldBe tiltak.navn
                prisinformasjon shouldBe prisinfoExpected
            }
        }
/*
    Slett kladd ligger fortsatt i pamelingService, uavhengig av om det er enkeltplass. Splitte opp?
        @Test
        fun `slettKladd - deltaker er KLADD - sletter deltaker og gjennomføring`() = runTest {
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                navBruker.personident,
            )

            pameldingService.slettKladd(deltakerInserted.id)

            deltakerRepository.get(deltakerInserted.id).shouldBeFailure()
            deltakerlisteRepository.get(deltakerInserted.deltakerliste.id).shouldBeFailure()
        }
        @Test
        fun `slettKladd - deltaker er KLADD men deltakerliste er syncet med valp - sletter ikke`() = runTest {
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                navBruker.personident,
            )
            deltakerlisteRepository.upsert(deltakerInserted.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES))

            shouldThrowAny {
                pameldingService.slettKladd(deltakerInserted.id)
            }
            deltakerRepository.get(deltakerInserted.id).shouldBeSuccess()
            deltakerlisteRepository.get(deltakerInserted.deltakerliste.id).shouldBeSuccess()
        }
 */
    }

    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, mockPersonServiceClient())

    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(navAnsattRepository, mockPersonServiceClient(), navEnhetService)

    private val deltakerRepository = DeltakerRepository()
    private val tiltakRepository = TiltakstypeRepository()

    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerProducerService = mockk(relaxed = true),
        deltakerEndringRepository = mockk(relaxed = true),
        deltakerEndringService = mockk(relaxed = true),
        vedtakRepository = mockk(relaxed = true),
        vedtakService = mockk(relaxed = true),
        hendelseService = mockk(relaxed = true),
        endringFraArrangorRepository = mockk(relaxed = true),
        importertFraArenaRepository = mockk(relaxed = true),
        deltakerHistorikkService = mockk(relaxed = true),
        navAnsattService = navAnsattService,
        endringFraTiltakskoordinatorRepository = mockk(relaxed = true),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(relaxed = true),
    )

    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val enkeltplassService = EnkeltplassService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = NavBrukerService(
            repository = NavBrukerRepository(),
            personServiceClient = mockPersonServiceClient(),
            enhetService = navEnhetService,
            ansattService = navAnsattService,
        ),
        gjennomforingRequestProducer = mockk(),
        deltakerlisteRepository = deltakerlisteRepository,
        tiltakRepository = tiltakRepository,
        navEnhetService = navEnhetService,
        navAnsattService = navAnsattService,
        vedtakService = mockk(relaxed = true),
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
