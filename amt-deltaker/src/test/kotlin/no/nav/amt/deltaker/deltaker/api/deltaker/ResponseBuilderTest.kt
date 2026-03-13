package no.nav.amt.deltaker.deltaker.api.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
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
import no.nav.amt.deltaker.navenhet.NavEnhetService
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
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ResponseBuilderTest {
    private val arrangorService: ArrangorService = mockk(relaxed = true)
    private val navAnsattRepository: NavAnsattRepository = mockk(relaxed = true)
    private val navEnhetRepository: NavEnhetRepository = mockk(relaxed = true)
    private val navEnhetService: NavEnhetService = mockk(relaxed = true)
    private val amtDistribusjonClient: AmtDistribusjonClient = mockk(relaxed = true)
    private val deltakerHistorikkService: DeltakerHistorikkService = mockk(relaxed = true)
    private val forslagRepository: ForslagRepository = mockk(relaxed = true)
    private val deltakerLaaseService: DeltakerLaaseService = mockk(relaxed = true)

    private val responseBuilder = ResponseBuilder(
        arrangorService = arrangorService,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        navEnhetService = navEnhetService,
        amtDistribusjonClient = amtDistribusjonClient,
        deltakerHistorikkService = deltakerHistorikkService,
        forslagRepository = forslagRepository,
        deltakerLaaseService = deltakerLaaseService,
    )

    @BeforeEach
    fun setup() = clearAllMocks()

    @Test
    fun `buildNavBrukerResponseFromNavBruker - skal mappe innbygger korrekt`() = runTest {
        // Arrange
        val navBruker = lagNavBruker(
            navVeilederId = UUID.randomUUID(),
            navEnhetId = UUID.randomUUID(),
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
        )

        every { navAnsattRepository.get(navBruker.navVeilederId.shouldNotBeNull()) } returns mockk {
            every { navn } returns "Nav-ansatt"
        }

        every { navEnhetRepository.get(navBruker.navEnhetId.shouldNotBeNull()) } returns mockk {
            every { navn } returns "Nav-enhet"
        }

        // Act
        val navBrukerResponse = responseBuilder.buildNavBrukerResponseFromNavBruker(navBruker)

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
        }
    }

    @Test
    fun `buildGjennomforingResponse - skal mappe deltakerliste korrekt`() = runTest {
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
    fun `buildDeltakerResponse - mapper vedtaksinformasjon korrekt`() = runTest {
        // Arrange
        val vedtaksinformasjon = lagVedtak(
            fattet = LocalDateTime.now(),
            fattetAvNav = true,
            deltakerVedVedtak = lagDeltaker(),
            sistEndretAvEnhet = lagNavEnhet(),
            sistEndretAv = lagNavAnsatt(),
        ).tilVedtaksInformasjon()

        every { navAnsattRepository.get(any<UUID>()) } answers {
            val id = firstArg<UUID>()

            mockk {
                every { navn } returns if (id == vedtaksinformasjon.opprettetAv) {
                    "Nav-ansatt 1"
                } else {
                    "Nav-ansatt 2"
                }
            }
        }

        every {
            navEnhetService.getEnheter(any())
        } returns mapOf(
            vedtaksinformasjon.opprettetAvEnhet to mockk { every { navn } returns "Nav Stovner" },
            vedtaksinformasjon.sistEndretAvEnhet to mockk { every { navn } returns "Nav Grunerløkka" },
        )

        // Act
        val vedtaksinformasjonResponse = responseBuilder.buildVedtaksinformasjonResponse(vedtaksinformasjon)

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
        val deltaker = lagDeltaker(
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            dagerPerUke = 4F,
            deltakelsesprosent = 50F,
            bakgrunnsinformasjon = "~bakgrunnsinformasjon~",
            innhold = Deltakelsesinnhold("~ledetekst~", emptyList()),
            erManueltDeltMedArrangor = true,
        )

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
