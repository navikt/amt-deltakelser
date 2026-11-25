package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.api.response.GjennomforingPrisinformasjon
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.felles.visningsnavn.TiltakVisningsnavn
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VisningsnavnResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.FALLBACK_DELTAKELSESPROSENT
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.utils.GenericCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerResponseBuilderTest : IntegrationTestBase() {
    override val arrangorService: ArrangorService = mockk()
    override val deltakerLaaseService: DeltakerLaaseService = mockk()
    override val navEnhetService: NavEnhetService = mockk()
    override val navAnsattService: NavAnsattService = mockk()
    override val deltakerHistorikkService: DeltakerHistorikkService = mockk()

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
        val navBrukerResponse = deltakerResponseBuilder.buildNavBrukerResponse(
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
        val deltakerliste = lagDeltakerliste(
            status = GjennomforingStatusType.GJENNOMFORES,
            startDato = LocalDate.now(),
            sluttDato = LocalDate.now().plusDays(1),
            oppstart = Oppstartstype.FELLES,
            oppmoteSted = "~oppmoteSted~",
            apentForPamelding = true,
            pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        // Act
        val gjennomforingResponse = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, false)

        // Assert
        val deltakerlisteArrangor = deltakerliste.arrangor.shouldNotBeNull()
        val expectedArrangor = ArrangorResponse(
            id = deltakerlisteArrangor.id,
            navn = deltakerlisteArrangor.navn,
            organisasjonsnummer = deltakerlisteArrangor.organisasjonsnummer,
        )

        val expectedVisningsnavn = TiltakVisningsnavn
            .lagVisningsnavn(
                tiltakskode = deltakerliste.tiltakstype.tiltakskode,
                tiltaksnavn = deltakerliste.tiltakstype.navn,
                gjennomforingsnavn = deltakerliste.navn,
                gjennomforingType = deltakerliste.gjennomforingstype,
                erKladd = false,
                arrangorNavn = deltakerliste.arrangor.shouldNotBeNull().navn,
                opplaringKategoriseringValg = deltakerliste.opplaringKategorisering,
            ).let { VisningsnavnResponse(it.aktivitetskortTittel) }

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
            visningsnavn shouldBe expectedVisningsnavn
        }
    }

    @Test
    fun `buildGjennomforingResponse - arrangørnavn med caps formateres i respons`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            arrangor = TestData.lagArrangor(navn = "TEST ARRANGØR"),
        )

        // Act
        val gjennomforingResponse = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, false)

        // Assert
        gjennomforingResponse.arrangor.shouldNotBeNull().navn shouldBe "Test Arrangør"
    }

    @Test
    fun `buildGjennomforingResponse - enkeltplass med includeKodeverk - henter kodeverk og sertifiseringer`() {
        // Arrange
        val opplaringKategoriseringValg = OpplaringKategoriseringValg(
            valgteSertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            ),
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(UUID.randomUUID() to "Bygg og anlegg"),
                ),
            ),
        )
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
            opplaringKategorisering = opplaringKategoriseringValg,
        )

        mockkObject(PrisinfoRepoAdapter)
        try {
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(deltakerliste.id)
            } returns emptyMap()

            // Act
            val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includePrisinfo = true)

            // Assert
            response.opplaringKategoriseringValg shouldBe opplaringKategoriseringValg
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
        }
    }

    @Test
    fun `buildGjennomforingResponse - ikke enkeltplass med includeKodeverk - returnerer ikke utflatet kodeverk`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Gruppe,
        )

        // Act
        val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includePrisinfo = true)

        // Assert
        response.opplaringKategoriseringValg shouldBe null
    }

    @Test
    fun `buildGjennomforingResponse - enkeltplass uten includeKodeverk - returnerer ikke utflatet kodeverk`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
        )

        // Act
        val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includePrisinfo = false)

        // Assert
        response.opplaringKategoriseringValg shouldBe null
    }

    @Test
    fun `buildGjennomforingResponse - HOYERE_UTDANNING - mapper gjeldende og ventende pris`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.HOYERE_UTDANNING,
            deltakerStatus = DeltakerStatus.Type.DELTAR,
        )
    }

    @Test
    fun `buildGjennomforingResponse - HOYERE_UTDANNING - mapper gjeldende og ventende pris for kladd`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.HOYERE_UTDANNING,
            deltakerStatus = DeltakerStatus.Type.KLADD,
        )
    }

    @Test
    fun `buildGjennomforingResponse - HOYERE_UTDANNING - mapper gjeldende og ventende pris for utkast`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.HOYERE_UTDANNING,
            deltakerStatus = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        )
    }

    @Test
    fun `buildGjennomforingResponse - ARBEIDSMARKEDSOPPLAERING - mapper gjeldende og ventende pris for utkast`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            deltakerStatus = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        )
    }

    @Test
    fun `buildGjennomforingResponse - ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING - mapper gjeldende og ventende pris`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            deltakerStatus = DeltakerStatus.Type.DELTAR,
        )
    }

    @Test
    fun `buildGjennomforingResponse - ENKELTPLASS_FAG_OG_YRKESOPPLAERING - mapper gjeldende og ventende pris`() {
        verifiserGjennomforingResponse(
            tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            deltakerStatus = DeltakerStatus.Type.DELTAR,
        )
    }

    private fun verifiserGjennomforingResponse(
        tiltakskode: Tiltakskode,
        deltakerStatus: DeltakerStatus.Type,
    ) {
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
            tiltakstype = lagTiltakstype(tiltakskode = tiltakskode),
        )
        val gjeldendePrisinfo = Anskaffelse(pris = 10000)
        val ventendePrisinfo = Anskaffelse(pris = 12000)

        mockkObject(PrisinfoRepoAdapter)
        try {
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(deltakerliste.id)
            } returns mapOf(
                PrisinfoDbo.Rolle.GJELDENDE to gjeldendePrisinfo,
                PrisinfoDbo.Rolle.ENDRING to ventendePrisinfo,
            )

            val response = deltakerResponseBuilder.buildGjennomforingResponse(
                gjennomforing = deltakerliste,
                includePrisinfo = true,
                deltakerStatus = deltakerStatus,
            )

            response.prisinformasjon shouldBe gjeldendePrisinfo
            response.prisinformasjonTilGodkjenning shouldBe ventendePrisinfo
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
        }
    }

    @Test
    fun `buildGjennomforingResponse - setter begrunnelse på matchende prisinformasjonTilGodkjenning`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
        )
        val gjeldendePrisinfo = Anskaffelse(pris = 10000)
        val forventetBegrunnelse = "Oppdatert etter ny dokumentasjon"
        val prisinfoTilGodkjenning = Anskaffelse(pris = 12000, begrunnelse = forventetBegrunnelse)

        mockkObject(PrisinfoRepoAdapter)
        try {
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(deltakerliste.id)
            } returns mapOf(
                PrisinfoDbo.Rolle.GJELDENDE to gjeldendePrisinfo,
                PrisinfoDbo.Rolle.ENDRING to prisinfoTilGodkjenning,
            )

            // Act
            val response = deltakerResponseBuilder.buildGjennomforingResponse(
                gjennomforing = deltakerliste,
                includePrisinfo = true,
            )

            // Assert
            // Begrunnelse skal kun følge ENDRING-prisinfo som matcher prisinformasjonTilGodkjenning.
            (response.prisinformasjon as Anskaffelse).begrunnelse shouldBe null
            (response.prisinformasjonTilGodkjenning as Anskaffelse).begrunnelse shouldBe forventetBegrunnelse
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
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
        val vedtaksinformasjonResponse = deltakerResponseBuilder.buildVedtaksinformasjonResponse(
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
        every { deltakerHistorikkService.getForDeltaker(any<java.util.UUID>(), any()) } returns emptyList()
        every { deltakerRepository.getSoktInnDato(any()) } returns null
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
        val deltakerResponse = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includePrisinfo = false)

        // Assert
        assertSoftly(deltakerResponse) {
            id shouldBe deltaker.id
            startdato shouldBe deltaker.startdato.shouldNotBeNull()
            sluttdato shouldBe deltaker.sluttdato.shouldNotBeNull()
            dagerPerUke shouldBe deltaker.dagerPerUke.shouldNotBeNull()
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent.shouldNotBeNull()
            deltakelsesmengder shouldBe null
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon.shouldNotBeNull()
            deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold.shouldNotBeNull()
            status shouldBe deltaker.status
            vedtaksinformasjon shouldBe null
            sistEndret shouldBe deltaker.sistEndret
            kilde shouldBe deltaker.kilde
            erManueltDeltMedArrangor shouldBe true
            opprettet shouldBe deltaker.opprettet

            erLaastForEndringer shouldBe true
            endringsforslagFraArrangor shouldBe expectedForslag
            sisteVurdering shouldBe VurderingResponse.fromVurdering(vurdering)
        }
    }

    @Nested
    inner class BuildDeltakerResponseMedDeltakelsesmengder {
        private fun setupMocks(
            navAnsatt: NavAnsatt,
            navEnhet: NavEnhet,
            historikk: List<DeltakerHistorikk>,
        ) {
            coEvery { distribusjonClient.digitalBruker(any()) } returns true
            every { deltakerLaaseService.erLaastForEndringer(any()) } returns false
            every { deltakerHistorikkService.getForDeltaker(any<java.util.UUID>(), any()) } returns historikk
            every { deltakerRepository.getSoktInnDato(any()) } returns null
            every { vurderingRepository.getForDeltaker(any()) } returns emptyList()
            every { forslagRepository.getForDeltaker(any()) } returns emptyList()
            coEvery { navAnsattService.hentNavAnsatteForDeltaker(any()) } returns GenericCache(
                cacheName = "navAnsatte",
                items = listOf(navAnsatt),
                idSelector = { it.id },
            )
            coEvery { navEnhetService.hentNavEnheterForDeltaker(any()) } returns GenericCache(
                cacheName = "navEnheter",
                items = listOf(navEnhet),
                idSelector = { it.id },
            )
        }

        @Test
        fun `ImportertFraArena, Vedtak og Endring i samme historikk - alle bidrar til deltakelsesmengder`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val arenaStartdato = LocalDate.now().minusMonths(6)
            val vedtakFattetDato = LocalDate.now().minusMonths(2)
            val endringGyldigFra = LocalDate.now().plusDays(14)

            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = arenaStartdato,
                sluttdato = arenaStartdato.plusYears(1),
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
            )

            // 1. Først importert fra Arena med 100%/5 dager
            val importertFraArena = DeltakerHistorikk.ImportertFraArena(
                TestData.lagImportertFraArena(
                    deltakerId = deltaker.id,
                    importertDato = arenaStartdato.atStartOfDay(),
                    deltakerVedImport = TestData.lagDeltakerVedImport(
                        startdato = arenaStartdato,
                        sluttdato = arenaStartdato.plusYears(1),
                        deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
                        dagerPerUke = 5F,
                    ),
                ),
            )

            // 2. Senere fattet Vedtak fra Nav med 80%/4 dager
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker.copy(deltakelsesprosent = 80F, dagerPerUke = 4F),
                fattet = vedtakFattetDato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )

            // 3. Fremtidig Endring til 50%/2 dager
            val fremtidigEndring = DeltakerHistorikk.Endring(
                lagDeltakerEndring(
                    deltakerId = deltaker.id,
                    endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                        deltakelsesprosent = 50F,
                        dagerPerUke = 2F,
                        gyldigFra = endringGyldigFra,
                        begrunnelse = null,
                    ),
                    endretAv = navAnsatt.id,
                    endretAvEnhet = navEnhet.id,
                    endret = LocalDateTime.now(),
                ),
            )

            setupMocks(
                navAnsatt,
                navEnhet,
                listOf(importertFraArena, DeltakerHistorikk.Vedtak(vedtak), fremtidigEndring),
            )

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includePrisinfo = false)

            // Assert
            // nesteDeltakelsesmengde = den fremtidige endringen (50%/2 dager fra +14 dager)
            // sisteDeltakelsesmengde = siste i tid = samme fremtidige endring
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 2F,
                    gyldigFra = endringGyldigFra,
                )
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 2F,
                    gyldigFra = endringGyldigFra,
                )
            }
            // ImportertFraArena er også speilet på top-level felt
            response.importertFraArena shouldBe importertFraArena.importertFraArena
            response.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            response.dagerPerUke shouldBe deltaker.dagerPerUke
        }

        @Test
        fun `EndringFraArrangor LeggTilOppstartsdato - henter full historikk og avgrenser perioden`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            // Deltaker uten egen Nav-startdato — arrangør har lagt til oppstartsdato
            val arrangorStartdato = LocalDate.now().plusDays(7)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = arrangorStartdato,
                sluttdato = arrangorStartdato.plusMonths(3),
                deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
                dagerPerUke = 5F,
            )

            // Vedtak fattet før arrangør la til oppstartsdato — vil i utgangspunktet gi
            // deltakelsesmengde med gyldigFra = vedtak.fattet (før arrangorStartdato)
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = LocalDate.now().minusDays(3).atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )

            // Arrangør legger til oppstartsdato — skal avgrense perioden
            val endringFraArrangor = DeltakerHistorikk.EndringFraArrangor(
                no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor(
                    deltakerId = deltaker.id,
                    endring = EndringFraArrangor.LeggTilOppstartsdato(
                        startdato = arrangorStartdato,
                        sluttdato = arrangorStartdato.plusMonths(3),
                    ),
                    opprettet = LocalDateTime.now(),
                ),
            )

            setupMocks(navAnsatt, navEnhet, listOf(DeltakerHistorikk.Vedtak(vedtak), endringFraArrangor))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includePrisinfo = false)

            // Assert
            // Regresjonstest: EndringFraArrangor må være en del av historikken som hentes til
            // deltakelsesmengder. Den ligger nå i kjernehistorikken (ikke utvidet), siden
            // `LeggTilOppstartsdato` brukes av `toDeltakelsesmengder.avgrensPeriodeTilStartdato`.
            verify { deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false) }

            // sisteDeltakelsesmengde.gyldigFra skal være arrangorStartdato (avgrenset),
            // ikke vedtak.fattet (3 dager tidligere)
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                sisteDeltakelsesmengde.shouldNotBeNull().gyldigFra shouldBe arrangorStartdato
            }
        }
    }

    @Nested
    inner class FinnPrisinfoTests {
        private val gjennomforingId = UUID.randomUUID()
        private val gjeldendePrisinfo = Anskaffelse(pris = 10000)
        private val endringPrisinfo = Anskaffelse(pris = 12000)

        @BeforeEach
        fun setup() = mockkObject(PrisinfoRepoAdapter)

        @AfterEach
        fun teardown() = unmockkObject(PrisinfoRepoAdapter)

        @Test
        fun `ingen prisinfo - returnerer null`() {
            // Arrange
            every { PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId) } returns emptyMap()

            // Act
            val funnet = GjennomforingPrisinformasjon.finnPrisInfo(gjennomforingId)

            // Assert
            funnet shouldBe null
        }

        @Test
        fun `kun ENDRING finnes med status UTKAST_TIL_PAMELDING - returnerer endringen som aktiv prisinfo`() {
            // Arrange
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)
            } returns mapOf(PrisinfoDbo.Rolle.ENDRING to endringPrisinfo)

            // Act
            val funnet = GjennomforingPrisinformasjon.finnPrisInfo(
                gjennomforingId,
                DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
            )

            // Assert
            funnet?.aktiv shouldBe endringPrisinfo
            funnet?.tilGodkjenning shouldBe null
        }

        @Test
        fun `kun ENDRING finnes, deltakerstatus DELTAR - returnerer endringen til godkjenning`() {
            // Arrange
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)
            } returns mapOf(PrisinfoDbo.Rolle.ENDRING to endringPrisinfo)

            // Act
            val funnet = GjennomforingPrisinformasjon.finnPrisInfo(
                gjennomforingId,
                DeltakerStatus.Type.DELTAR,
            )

            // Assert
            funnet!!.aktiv shouldBe null
            funnet.tilGodkjenning shouldBe endringPrisinfo
        }

        @Test
        fun `kun GJELDENDE finnes - returnerer Funnet(gjeldende, null)`() {
            // Arrange
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)
            } returns mapOf(PrisinfoDbo.Rolle.GJELDENDE to gjeldendePrisinfo)

            // Act
            val funnet = GjennomforingPrisinformasjon.finnPrisInfo(gjennomforingId)

            // Assert
            funnet!!.aktiv shouldBe gjeldendePrisinfo
            funnet.tilGodkjenning shouldBe null
        }

        @Test
        fun `GJELDENDE og ENDRING finnes - returnerer Funnet(gjeldende, endring)`() {
            // Arrange
            every {
                PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)
            } returns mapOf(
                PrisinfoDbo.Rolle.GJELDENDE to gjeldendePrisinfo,
                PrisinfoDbo.Rolle.ENDRING to endringPrisinfo,
            )

            // Act
            val funnet = GjennomforingPrisinformasjon.finnPrisInfo(gjennomforingId)

            // Assert
            funnet!!.aktiv shouldBe gjeldendePrisinfo
            funnet.tilGodkjenning shouldBe endringPrisinfo
        }
    }
}
