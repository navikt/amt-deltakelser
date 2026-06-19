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
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.OpplaeringKategoriseringRepoAdapter
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.UtflatetKodeverk
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
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

        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"

        // Act
        val gjennomforingResponse = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, false)

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
    fun `buildGjennomforingResponse - enkeltplass med includeKodeverk - henter kodeverk og sertifiseringer`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
        )

        val utflatetKodeverk = UtflatetKodeverk(
            valgteSertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            ),
            valgteKategoriseringer = setOf(
                UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    valg = mapOf(UUID.randomUUID() to "Bygg og anlegg"),
                ),
            ),
        )

        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
        mockkObject(OpplaeringKategoriseringRepoAdapter)
        try {
            every {
                OpplaeringKategoriseringRepoAdapter.hentUtflatetKodeverk(deltakerliste.id)
            } returns utflatetKodeverk

            // Act
            val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includeKodeverk = true)

            // Assert
            response.utflatetKodeverk shouldBe utflatetKodeverk
        } finally {
            unmockkObject(OpplaeringKategoriseringRepoAdapter)
        }
    }

    @Test
    fun `buildGjennomforingResponse - ikke enkeltplass med includeKodeverk - returnerer ikke utflatet kodeverk`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Gruppe,
        )

        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"

        // Act
        val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includeKodeverk = true)

        // Assert
        response.utflatetKodeverk shouldBe null
    }

    @Test
    fun `buildGjennomforingResponse - enkeltplass uten includeKodeverk - returnerer ikke utflatet kodeverk`() {
        // Arrange
        val deltakerliste = lagDeltakerliste(
            gjennomforingstype = GjennomforingType.Enkeltplass,
        )

        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"

        // Act
        val response = deltakerResponseBuilder.buildGjennomforingResponse(deltakerliste, includeKodeverk = false)

        // Assert
        response.utflatetKodeverk shouldBe null
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
        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
        every { deltakerHistorikkService.getForDeltaker(any(), any()) } returns emptyList()
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
        val deltakerResponse = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

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

            erLaastForEndringer shouldBe true
            endringsforslagFraArrangor shouldBe expectedForslag
            prisinformasjon shouldBe deltaker.deltakerliste.prisinformasjon
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
            every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
            every { deltakerHistorikkService.getForDeltaker(any(), any()) } returns historikk
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

        private fun endreDeltakelsesmengde(
            deltakerId: UUID,
            navAnsatt: NavAnsatt,
            navEnhet: NavEnhet,
            deltakelsesprosent: Float,
            dagerPerUke: Float?,
            gyldigFra: LocalDate,
            endret: LocalDateTime,
        ) = DeltakerHistorikk.Endring(
            no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
                deltakerId = deltakerId,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = deltakelsesprosent,
                    dagerPerUke = dagerPerUke,
                    gyldigFra = gyldigFra,
                    begrunnelse = null,
                ),
                endretAv = navAnsatt.id,
                endretAvEnhet = navEnhet.id,
                endret = endret,
            ),
        )

        @Test
        fun `tom historikk - returnerer DeltakelsesmengderResponse med null felter`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusMonths(3),
            )

            setupMocks(navAnsatt, navEnhet, emptyList())

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe null
                sisteDeltakelsesmengde shouldBe null
            }
        }

        @Test
        fun `kun gjeldende vedtak - sisteDeltakelsesmengde settes, nesteDeltakelsesmengde er null`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val startdato = LocalDate.now().minusMonths(1)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(3),
                deltakelsesprosent = 80F,
                dagerPerUke = 4F,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = startdato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )

            setupMocks(navAnsatt, navEnhet, listOf(DeltakerHistorikk.Vedtak(vedtak)))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe null
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 80F,
                    dagerPerUke = 4F,
                    gyldigFra = startdato,
                )
            }
        }

        @Test
        fun `gjeldende og fremtidig endring - nesteDeltakelsesmengde mapper fremtidig, siste mapper fremtidig (lastOrNull)`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val startdato = LocalDate.now().minusMonths(1)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(6),
                deltakelsesprosent = 100F,
                dagerPerUke = 5F,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = startdato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )
            val fremtidigGyldigFra = LocalDate.now().plusDays(7)
            val fremtidig = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = 60F,
                dagerPerUke = 3F,
                gyldigFra = fremtidigGyldigFra,
                endret = LocalDateTime.now(),
            )

            setupMocks(navAnsatt, navEnhet, listOf(DeltakerHistorikk.Vedtak(vedtak), fremtidig))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            val fremtidigResponse = DeltakelsesmengdeResponse(
                deltakelsesprosent = 60F,
                dagerPerUke = 3F,
                gyldigFra = fremtidigGyldigFra,
            )
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe fremtidigResponse
                sisteDeltakelsesmengde shouldBe fremtidigResponse
            }
        }

        @Test
        fun `mengder før startdato trimmes bort av periode-funksjonen`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            // Startdato i framtiden gjør at "tidligere" endringer havner før perioden
            val startdato = LocalDate.now().plusDays(10)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(3),
                deltakelsesprosent = 100F,
                dagerPerUke = 5F,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = LocalDate.now().minusMonths(2).atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )
            // Endring som gjelder fra startdato (innenfor perioden)
            val gyldigFraInnenforPeriode = startdato.plusDays(1)
            val innenforPeriode = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = gyldigFraInnenforPeriode,
                endret = LocalDateTime.now(),
            )

            setupMocks(navAnsatt, navEnhet, listOf(DeltakerHistorikk.Vedtak(vedtak), innenforPeriode))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            // Vedtaket før startdato avgrenses til startdato, og endringen innenfor blir siste/neste
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 2F,
                    gyldigFra = gyldigFraInnenforPeriode,
                )
                nesteDeltakelsesmengde.shouldNotBeNull()
            }
        }

        @Test
        fun `uten startdato - bruker hele tidslinjen uten periode-trim`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = null,
                sluttdato = null,
                deltakelsesprosent = 100F,
                dagerPerUke = 5F,
            )
            val fattetDato = LocalDate.now().minusMonths(1)
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = fattetDato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )

            setupMocks(navAnsatt, navEnhet, listOf(DeltakerHistorikk.Vedtak(vedtak)))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 100F,
                    dagerPerUke = 5F,
                    gyldigFra = fattetDato,
                )
                nesteDeltakelsesmengde shouldBe null
            }
        }

        @Test
        fun `ImportertFraArena - brukes som basis-deltakelsesmengde`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val startdato = LocalDate.now().minusMonths(1)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(3),
                deltakelsesprosent = 75F,
                dagerPerUke = 3F,
            )
            val importertFraArena = DeltakerHistorikk.ImportertFraArena(
                TestData.lagImportertFraArena(
                    deltakerId = deltaker.id,
                    importertDato = startdato.atStartOfDay(),
                    deltakerVedImport = TestData.lagDeltakerVedImport(
                        startdato = startdato,
                        sluttdato = startdato.plusMonths(3),
                        deltakelsesprosent = 75F,
                        dagerPerUke = 3F,
                    ),
                ),
            )

            setupMocks(navAnsatt, navEnhet, listOf(importertFraArena))

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe null
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 75F,
                    dagerPerUke = 3F,
                    gyldigFra = startdato,
                )
            }
        }

        @Test
        fun `to fremtidige endringer - nesteGjeldende er naermeste, sisteDeltakelsesmengde er sist i tid`() = runTest {
            // Arrange
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val startdato = LocalDate.now().minusMonths(1)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(6),
                deltakelsesprosent = 100F,
                dagerPerUke = 5F,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = startdato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )
            val naermesteFremtidigGyldigFra = LocalDate.now().plusDays(7)
            val naermesteFremtidig = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = 75F,
                dagerPerUke = 4F,
                gyldigFra = naermesteFremtidigGyldigFra,
                endret = LocalDateTime.now().minusDays(2),
            )
            val fjernesteFremtidigGyldigFra = LocalDate.now().plusMonths(2)
            val fjernesteFremtidig = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = fjernesteFremtidigGyldigFra,
                endret = LocalDateTime.now().minusDays(1),
            )

            setupMocks(
                navAnsatt,
                navEnhet,
                listOf(DeltakerHistorikk.Vedtak(vedtak), naermesteFremtidig, fjernesteFremtidig),
            )

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

            // Assert
            assertSoftly(response.deltakelsesmengder.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 75F,
                    dagerPerUke = 4F,
                    gyldigFra = naermesteFremtidigGyldigFra,
                )
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 2F,
                    gyldigFra = fjernesteFremtidigGyldigFra,
                )
            }
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
                        deltakelsesprosent = 100F,
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
            val fremtidigEndring = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = endringGyldigFra,
                endret = LocalDateTime.now(),
            )

            setupMocks(
                navAnsatt,
                navEnhet,
                listOf(importertFraArena, DeltakerHistorikk.Vedtak(vedtak), fremtidigEndring),
            )

            // Act
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

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
                deltakelsesprosent = 100F,
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
            val response = deltakerResponseBuilder.buildDeltakerResponse(deltaker, includeKodeverk = false)

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
}
