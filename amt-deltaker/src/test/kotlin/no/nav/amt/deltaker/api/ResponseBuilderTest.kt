package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.utils.GenericCache
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ResponseBuilderTest : IntegrationTestBase() {
    override val arrangorService: ArrangorService = mockk()
    override val deltakerLaaseService: DeltakerLaaseService = mockk()
    override val navEnhetService: NavEnhetService = mockk()
    override val navAnsattService: NavAnsattService = mockk()
    override val deltakerHistorikkService: DeltakerHistorikkService = mockk()

    @Nested
    inner class CacheTests {
        val idInTest: UUID = UUID.randomUUID()
        val cache = GenericCache(
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
        val navBruker = TestData.lagNavBruker(
            navVeilederId = UUID.randomUUID(),
            navEnhetId = UUID.randomUUID(),
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
            telefon = "12345678",
            epost = "a@b.c",
        )

        val navAnsattCache: GenericCache<NavAnsatt> = mockk()
        val navEnhetCache: GenericCache<NavEnhet> = mockk()

        coEvery { distribusjonClient.digitalBruker(navBruker.personident) } returns true
        val navVeilederExpected = NavAnsatt(
            id = UUID.randomUUID(),
            navn = "Nav Veiledersen",
            epost = "Nav-ansatt@tr.no",
            navIdent = "z123",
            telefon = "1234",
            navEnhetId = UUID.randomUUID(),
        )
        every { navAnsattCache.getOrThrow(navBruker.navVeilederId.shouldNotBeNull()) } returns navVeilederExpected

        every { navEnhetCache.getOrThrow(navBruker.navEnhetId.shouldNotBeNull()) } returns mockk {
            every { navn } returns "Nav-enhet"
        }

        // Act
        val navBrukerResponse = responseBuilder.buildNavBrukerResponseFromNavBruker(
            navBruker = navBruker,
            navAnsatte = navAnsattCache,
            navEnheter = navEnhetCache,
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
            navVeileder shouldBe NavVeilederResponse(
                navn = navVeilederExpected.navn,
                epost = navVeilederExpected.epost,
                telefonnummer = navVeilederExpected.telefon,
            )
            navEnhet shouldBe "Nav-enhet"
            innsatsgruppe shouldBe navBruker.innsatsgruppe.shouldNotBeNull()
            erDigital shouldBe true
        }
    }

    @Test
    fun `buildGjennomforingResponse - skal mappe deltakerliste korrekt`() {
        // Arrange
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
            status = GjennomforingStatusType.GJENNOMFORES,
            startDato = LocalDate.now(),
            sluttDato = LocalDate.now().plusDays(1),
            oppstart = Oppstartstype.FELLES,
            oppmoteSted = "~oppmoteSted~",
            apentForPamelding = true,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"

        // Act
        val gjennomforingResponse = responseBuilder.buildGjennomforingResponse(deltakerliste)

        // Assert
        val expectedArrangor = ArrangorResponse(
            navn = "~arrangor-navn~",
            deltakerliste.arrangor.shouldNotBeNull().organisasjonsnummer,
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
        val vedtaksinformasjon = no.nav.amt.deltaker.utils.data.TestData
            .lagVedtak(
                fattet = LocalDateTime.now(),
                fattetAvNav = true,
                deltakerVedVedtak = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltaker(),
                sistEndretAvEnhet = TestData.lagNavEnhet(),
                sistEndretAv = TestData.lagNavAnsatt(),
            ).tilVedtaksInformasjon()

        val navAnsattCache: GenericCache<NavAnsatt> = mockk()
        val navEnhetCache: GenericCache<NavEnhet> = mockk()

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
            navAnsatte = navAnsattCache,
            navEnheter = navEnhetCache,
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
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()

        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
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
        val vurdering = Vurdering(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            opprettetAvArrangorAnsattId = UUID.randomUUID(),
            gyldigFra = LocalDateTime.now(),
            vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            begrunnelse = null,
        )

        coEvery { distribusjonClient.digitalBruker(deltaker.navBruker.personident) } returns true
        every { deltakerLaaseService.erLaastForEndringer(deltaker) } returns true
        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns emptyList()
        every { vurderingRepository.getForDeltaker(deltaker.id) } returns listOf(vurdering)

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

        coEvery { navAnsattService.hentNavAnsatteForDeltaker(deltaker) } returns GenericCache(
            cacheName = "navAnsatte",
            items = listOf(navAnsatt),
            idSelector = { it.id },
        )

        coEvery { navEnhetService.hentNavEnheterForDeltaker(deltaker) } returns GenericCache(
            cacheName = "navEnheter",
            items = listOf(navEnhet),
            idSelector = { it.id },
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
            prisinformasjon shouldBe deltaker.deltakerliste.prisinformasjon
            sisteVurdering shouldBe VurderingResponse.fromVurdering(vurdering)
        }
    }

    @Test
    fun `buildDeltakereResponse - mapper hver deltaker via buildDeltakerResponse`() = runTest {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()

        val deltaker1 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
        )
        val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
        )
        val deltakere = listOf(deltaker1, deltaker2)

        coEvery { distribusjonClient.digitalBruker(any()) } returns true
        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
        every { deltakerLaaseService.erLaastForEndringer(any()) } returns false
        every { deltakerHistorikkService.getForDeltaker(any()) } returns emptyList()
        every { vurderingRepository.getForDeltaker(any()) } returns emptyList()
        every { forslagRepository.getForDeltaker(any()) } returns emptyList()

        deltakere.forEach { deltaker ->
            coEvery { navAnsattService.hentNavAnsatteForDeltaker(deltaker) } returns GenericCache(
                cacheName = "navAnsatte",
                items = listOf(navAnsatt),
                idSelector = { it.id },
            )
            coEvery { navEnhetService.hentNavEnheterForDeltaker(deltaker) } returns GenericCache(
                cacheName = "navEnheter",
                items = listOf(navEnhet),
                idSelector = { it.id },
            )
        }

        // Act
        val deltakereResponse = responseBuilder.buildDeltakereResponse(deltakere)

        // Assert
        deltakereResponse.deltakere.size shouldBe 2
        deltakereResponse.deltakere[0].id shouldBe deltaker1.id
        deltakereResponse.deltakere[1].id shouldBe deltaker2.id
    }

    @Test
    fun `buildDeltakereResponse - tom liste returnerer tom respons`() = runTest {
        val deltakereResponse = responseBuilder.buildDeltakereResponse(emptyList())

        deltakereResponse.deltakere shouldBe emptyList()
    }
}
