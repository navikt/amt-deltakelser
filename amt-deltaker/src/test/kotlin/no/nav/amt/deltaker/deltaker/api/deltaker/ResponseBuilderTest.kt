package no.nav.amt.deltaker.deltaker.api.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerLaaseService
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.ArrangorResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.emptyList

class ResponseBuilderTest {
    private val arrangorService: ArrangorService = mockk(relaxed = true)
    private val navAnsattRepository: NavAnsattRepository = mockk(relaxed = true)
    private val navEnhetRepository: NavEnhetRepository = mockk(relaxed = true)
    private val amtDistribusjonClient: AmtDistribusjonClient = mockk()
    private val deltakerHistorikkService: DeltakerHistorikkService = mockk(relaxed = true)
    private val forslagRepository: ForslagRepository = mockk(relaxed = true)
    private val deltakerLaaseService: DeltakerLaaseService = mockk(relaxed = true)

    private val responseBuilder = ResponseBuilder(
        arrangorService = arrangorService,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        amtDistribusjonClient = amtDistribusjonClient,
        deltakerHistorikkService = deltakerHistorikkService,
        forslagRepository = forslagRepository,
        deltakerLaaseService = deltakerLaaseService,
    )

    @BeforeEach
    fun setup() = clearAllMocks()

    @Nested
    inner class CacheTests {
        val idInTest: UUID = UUID.randomUUID()
        val cache = ResponseBuilder.GenericCache(
            cacheName = "fooCache",
            items = listOf("foo"),
            idSelector = { idInTest },
        )

        @Test
        fun `getOrThrow - skal returnere cachet verdi`() {
            cache.getOrThrow(idInTest) shouldBe "foo"
        }

        @Test
        fun `getOrThrow - skal kaste feil hvis nokkel ikke finnes i cache`() {
            shouldThrow<NoSuchElementException> {
                cache.getOrThrow(UUID.randomUUID())
            }
        }
    }

    @Test
    fun `buildNavBrukerResponseFromNavBruker - skal mappe innbygger korrekt`() = runTest {
        // Arrange
        val navBruker = lagNavBruker(
            navVeilederId = UUID.randomUUID(),
            navEnhetId = UUID.randomUUID(),
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
        )

        val navAnsattCache: ResponseBuilder.GenericCache<NavAnsatt> = mockk()
        val navEnhetCache: ResponseBuilder.GenericCache<NavEnhet> = mockk()

        coEvery { amtDistribusjonClient.digitalBruker(navBruker.personident) } returns true

        every { navAnsattCache.getOrThrow(navBruker.navVeilederId.shouldNotBeNull()) } returns mockk {
            every { navn } returns "Nav-ansatt"
        }

        every { navEnhetCache.getOrThrow(navBruker.navEnhetId.shouldNotBeNull()) } returns mockk {
            every { navn } returns "Nav-enhet"
        }

        // Act
        val navBrukerResponse = responseBuilder.buildNavBrukerResponseFromNavBruker(
            navBruker = navBruker,
            navAnsattCache = navAnsattCache,
            navEnhetCache = navEnhetCache,
        )

        // Assert
        assertSoftly(navBrukerResponse) {
            personident shouldBe navBruker.personident
            fornavn shouldBe navBruker.fornavn
            mellomnavn shouldBe navBruker.mellomnavn.shouldNotBeNull()
            etternavn shouldBe navBruker.etternavn
            telefon shouldBe navBruker.telefon.shouldNotBeNull()
            epost shouldBe navBruker.epost.shouldNotBeNull()
            erSkjermet shouldBe true
            adresse shouldBe navBruker.adresse.shouldNotBeNull()
            adressebeskyttelse shouldBe navBruker.adressebeskyttelse.shouldNotBeNull()
            navVeileder shouldBe "Nav-ansatt"
            navEnhet shouldBe "Nav-enhet"
            innsatsgruppe shouldBe navBruker.innsatsgruppe.shouldNotBeNull()
            erDigital shouldBe true
        }
    }

    @Test
    fun `buildGjennomforingResponse - skal mappe deltakerliste korrekt`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            status = GjennomforingStatusType.GJENNOMFORES,
            startDato = LocalDate.now(),
            sluttDato = LocalDate.now().plusDays(1),
            oppstart = Oppstartstype.FELLES,
            oppmoteSted = "~oppmoteSted~",
            apentForPamelding = true,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        every { arrangorService.getArrangorNavn(any()) } returns "~arrangor-navn~"

        // Act
        val gjennomforingResponse = responseBuilder.buildGjennomforingResponse(deltakerliste)

        // Assert
        val expectedArrangor = ArrangorResponse(
            navn = "~arrangor-navn~",
            deltakerliste.arrangor.organisasjonsnummer,
        )

        assertSoftly(gjennomforingResponse) {
            id shouldBe deltakerliste.id
            tiltakstype shouldBe deltakerliste.tiltakstype
            navn shouldBe deltakerliste.navn
            status shouldBe deltakerliste.status.shouldNotBeNull()
            startDato shouldBe deltakerliste.startDato.shouldNotBeNull()
            sluttDato shouldBe deltakerliste.sluttDato.shouldNotBeNull()
            oppstart shouldBe deltakerliste.oppstart.shouldNotBeNull()
            apentForPamelding shouldBe true
            oppmoteSted shouldBe deltakerliste.oppmoteSted.shouldNotBeNull()
            arrangor shouldBe expectedArrangor
            pameldingstype shouldBe deltakerliste.pameldingstype.shouldNotBeNull()
        }
    }

    @Test
    fun `buildVedtaksinformasjonResponse - mapper vedtaksinformasjon korrekt`() {
        // Arrange
        val vedtaksinformasjon = lagVedtak(
            fattet = LocalDateTime.now(),
            fattetAvNav = true,
            deltakerVedVedtak = lagDeltaker(),
            sistEndretAvEnhet = lagNavEnhet(),
            sistEndretAv = lagNavAnsatt(),
        ).tilVedtaksInformasjon()

        val navAnsattCache: ResponseBuilder.GenericCache<NavAnsatt> = mockk()
        val navEnhetCache: ResponseBuilder.GenericCache<NavEnhet> = mockk()

        every { navAnsattCache.getOrThrow(vedtaksinformasjon.opprettetAv) } returns mockk {
            every { navn } returns "Nav-ansatt 1"
        }

        every { navAnsattCache.getOrThrow(vedtaksinformasjon.sistEndretAv) } returns mockk {
            every { navn } returns "Nav-ansatt 2"
        }

        every { navEnhetCache.getOrThrow(vedtaksinformasjon.opprettetAvEnhet) } returns mockk {
            every { navn } returns "Nav Stovner"
        }

        every { navEnhetCache.getOrThrow(vedtaksinformasjon.sistEndretAvEnhet) } returns mockk {
            every { navn } returns "Nav Grunerløkka"
        }

        // Act
        val vedtaksinformasjonResponse = responseBuilder.buildVedtaksinformasjonResponse(
            vedtaksinformasjon = vedtaksinformasjon,
            navAnsattCache = navAnsattCache,
            navEnhetCache = navEnhetCache,
        )

        // Assert
        assertSoftly(vedtaksinformasjonResponse.shouldNotBeNull()) {
            fattet shouldBe vedtaksinformasjon.fattet.shouldNotBeNull()
            fattetAvNav shouldBe true
            opprettet shouldBe vedtaksinformasjon.opprettet
            opprettetAv shouldBe "Nav-ansatt 1"
            opprettetAvEnhet shouldBe "Nav Stovner"
            sistEndretAv shouldBe "Nav-ansatt 2"
            sistEndretAvEnhet shouldBe "Nav Grunerløkka"
        }
    }

    @Test
    fun `buildDeltakerResponse - mapper felter korrekt`() = runTest {
        // Arrange
        val navAnsatt = lagNavAnsatt()
        val navEnhet = lagNavEnhet()

        val deltaker = lagDeltaker(
            navBruker = lagNavBruker(
                navVeilederId = navAnsatt.id,
                navEnhetId = navEnhet.id,
            ),
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            dagerPerUke = 4F,
            deltakelsesprosent = 50F,
            bakgrunnsinformasjon = "~bakgrunnsinformasjon~",
            innhold = Deltakelsesinnhold("~ledetekst~", emptyList()),
            erManueltDeltMedArrangor = true,
        )

        coEvery { amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident) } returns true
        every { deltakerLaaseService.erLaastForEndringer(any()) } returns true

        val expectedForslag = listOf(
            Forslag(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now(),
                begrunnelse = "~begrunnelse~",
                endring = Forslag.ForlengDeltakelse(LocalDate.now().plusWeeks(2)),
                status = Forslag.Status.VenterPaSvar,
            ),
        )

        every { navAnsattRepository.getManyById(any()) } returns listOf(navAnsatt)
        every { navEnhetRepository.getMany(any()) } returns listOf(navEnhet)
        every { forslagRepository.getForDeltaker(any()) } returns expectedForslag

        // Act
        val deltakerResponse = responseBuilder.buildDeltakerResponse(deltaker)

        // Assert
        assertSoftly(deltakerResponse) {
            id shouldBe deltaker.id
            startdato shouldBe deltaker.startdato.shouldNotBeNull()
            sluttdato shouldBe deltaker.sluttdato.shouldNotBeNull()
            dagerPerUke shouldBe deltaker.dagerPerUke.shouldNotBeNull()
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent.shouldNotBeNull()
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon.shouldNotBeNull()
            deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold.shouldNotBeNull()
            status shouldBe deltaker.status
            vedtaksinformasjon shouldBe null
            sistEndret shouldBe deltaker.sistEndret
            kilde shouldBe deltaker.kilde
            erManueltDeltMedArrangor shouldBe true
            opprettet shouldBe deltaker.opprettet

            historikk shouldBe emptyList()
            erLaastForEndringer shouldBe true
            endringsforslagFraArrangor shouldBe expectedForslag
        }
    }
}
