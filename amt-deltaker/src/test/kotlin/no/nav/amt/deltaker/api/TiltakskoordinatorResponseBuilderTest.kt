package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.utils.GenericCache
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorResponseBuilderTest : IntegrationTestBase() {
    override val arrangorService: ArrangorService = mockk()
    override val navEnhetService: NavEnhetService = mockk()
    override val navAnsattService: NavAnsattService = mockk()
    override val deltakerHistorikkService: DeltakerHistorikkService = mockk()
    override val deltakerLaaseService: DeltakerLaaseService = mockk()

    @Test
    fun `buildResponse - tom liste - returnerer tom respons uten tunge oppslag`() = runTest {
        // Arrange — ingen deltakere, ingen mocker

        // Act
        val response = tiltakskoordinatorResponseBuilder.buildResponse(emptyList())

        // Assert
        response.deltakere shouldBe emptyList()
        coVerify(exactly = 0) { arrangorService.getArrangorNavn(any(), any()) }
        coVerify(exactly = 0) { navAnsattService.hentNavAnsatteForDeltakere(any()) }
        coVerify(exactly = 0) { navEnhetService.hentNavEnheterForDeltakere(any()) }
        coVerify(exactly = 0) { deltakerLaaseService.erLaastForEndringerForDeltakere(any()) }
        coVerify(exactly = 0) { distribusjonClient.digitalBruker(any()) }
        coVerify(exactly = 0) { deltakerHistorikkService.getSoktInnDatoer(any()) }
        coVerify(exactly = 0) { forslagRepository.getVenterPaSvarForDeltakere(any()) }
        coVerify(exactly = 0) { vurderingRepository.getSisteVurderingForDeltakere(any()) }
    }

    @Test
    fun `buildResponse - flere deltakere - bygger gjennomforing og henter bulk-cacher kun en gang`() = runTest {
        // Arrange — to deltakere som hører til samme deltakerliste
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()

        val deltaker1 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            deltakerliste = deltakerliste,
        )
        val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            deltakerliste = deltakerliste,
        )

        setupFellesMocker(navAnsatt, navEnhet)

        // Act
        val response = tiltakskoordinatorResponseBuilder.buildResponse(listOf(deltaker1, deltaker2))

        // Assert
        response.deltakere.size shouldBe 2
        // gjennomforing-objektet skal være delt mellom deltakerne (samme instans / verdi)
        response.deltakere[0].gjennomforing shouldBe response.deltakere[1].gjennomforing

        // arrangør-navn-oppslag skal kun skje én gang for hele lista
        coVerify(exactly = 1) { arrangorService.getArrangorNavn(any(), any()) }

        // bulk-cacher skal kalles én gang totalt — ikke per deltaker
        coVerify(exactly = 1) { navAnsattService.hentNavAnsatteForDeltakere(any()) }
        coVerify(exactly = 1) { navEnhetService.hentNavEnheterForDeltakere(any()) }

        // låsing skal beregnes via bulk-metoden i én spørring — ikke per deltaker
        coVerify(exactly = 1) { deltakerLaaseService.erLaastForEndringerForDeltakere(any()) }
        coVerify(exactly = 0) { deltakerLaaseService.erLaastForEndringer(any<no.nav.amt.deltaker.model.Deltaker>()) }

        // digital-status slås opp én gang per deltaker (Caffeine-cache i klienten dedupliserer på personident)
        coVerify(exactly = 2) { distribusjonClient.digitalBruker(any()) }

        // soktInnDato hentes i ett bulk-oppslag — ikke per deltaker
        coVerify(exactly = 1) { deltakerHistorikkService.getSoktInnDatoer(any()) }
        coVerify(exactly = 0) { deltakerHistorikkService.getSoktInnDato(any()) }
        coVerify(exactly = 0) { deltakerHistorikkService.getForDeltaker(any(), any()) }
    }

    @Test
    fun `buildResponse - mapper deltakerfelter korrekt og setter optimaliseringskonstantene`() = runTest {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            dagerPerUke = 4F,
            deltakelsesprosent = 50F,
            bakgrunnsinformasjon = "~bakgrunnsinformasjon~",
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
        val forslag = listOf(
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

        setupFellesMocker(navAnsatt, navEnhet, vurdering = vurdering, forslag = forslag)

        // Act
        val response = tiltakskoordinatorResponseBuilder.buildResponse(listOf(deltaker))
        val deltakerResponse = response.deltakere.single()

        // Assert
        assertSoftly(deltakerResponse) {
            id shouldBe deltaker.id
            startdato shouldBe deltaker.startdato.shouldNotBeNull()
            sluttdato shouldBe deltaker.sluttdato.shouldNotBeNull()
            dagerPerUke shouldBe deltaker.dagerPerUke.shouldNotBeNull()
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent.shouldNotBeNull()
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon.shouldNotBeNull()
            status shouldBe deltaker.status
            erManueltDeltMedArrangor shouldBe true
            sistEndret shouldBe deltaker.sistEndret
            kilde shouldBe deltaker.kilde
            opprettet shouldBe deltaker.opprettet
            prisinformasjon shouldBe deltaker.deltakerliste.prisinformasjon
            endringsforslagFraArrangor shouldBe forslag
            sisteVurdering shouldBe VurderingResponse.fromVurdering(vurdering)

            // Optimaliseringskonstanter — disse skal ALDRI variere fra tiltakskoordinator-flyten
            navBruker.erDigital shouldBe true // mocket via distribusjonClient.digitalBruker
            vedtaksinformasjon shouldBe null
            importertFraArena shouldBe null
            erLaastForEndringer shouldBe false
            deltakelsesmengder shouldBe null
            // kodeverkValg hentes aldri for koordinator-lista
            gjennomforing.kodeverkValg shouldBe emptySet()
        }
    }

    @Test
    fun `buildResponse - soktInnDato hentes i bulk for alle deltakere`() = runTest {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
        )
        val forventetSoktInn = LocalDate.now().minusMonths(1)

        setupFellesMocker(navAnsatt, navEnhet, soktInnDato = forventetSoktInn)

        // Act
        val response = tiltakskoordinatorResponseBuilder.buildResponse(listOf(deltaker))

        // Assert
        response.deltakere.single().soktInnDato shouldBe forventetSoktInn
        coVerify(exactly = 1) { deltakerHistorikkService.getSoktInnDatoer(setOf(deltaker.id)) }
        coVerify(exactly = 0) { deltakerHistorikkService.getSoktInnDato(any()) }
    }

    @Test
    fun `buildResponse - forslag og vurdering hentes i bulk for alle deltakere`() = runTest {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()
        val deltaker1 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            deltakerliste = deltakerliste,
        )
        val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            deltakerliste = deltakerliste,
        )

        val forslag1 = lagForslag(
            deltakerId = deltaker1.id,
            status = Forslag.Status.VenterPaSvar,
        )
        val forslag2 = lagForslag(
            deltakerId = deltaker2.id,
            status = Forslag.Status.VenterPaSvar,
        )
        val vurdering1 = lagVurdering(
            deltakerId = deltaker1.id,
            gyldigFra = LocalDateTime.now(),
        )

        setupFellesMocker(
            navAnsatt,
            navEnhet,
            forslag = listOf(forslag1, forslag2),
            vurderingListe = listOf(vurdering1),
        )

        // Act
        val response = tiltakskoordinatorResponseBuilder.buildResponse(listOf(deltaker1, deltaker2))

        // Assert
        response.deltakere[0].endringsforslagFraArrangor shouldBe listOf(forslag1)
        response.deltakere[1].endringsforslagFraArrangor shouldBe listOf(forslag2)
        response.deltakere[0].sisteVurdering shouldBe VurderingResponse.fromVurdering(vurdering1)
        response.deltakere[1].sisteVurdering shouldBe null

        // bulk-metoder kalles én gang — ikke per deltaker
        coVerify(exactly = 1) { forslagRepository.getVenterPaSvarForDeltakere(any()) }
        coVerify(exactly = 0) { forslagRepository.getForDeltaker(any()) }
        coVerify(exactly = 1) { vurderingRepository.getSisteVurderingForDeltakere(any()) }
        coVerify(exactly = 0) { vurderingRepository.getForDeltaker(any()) }
    }

    private fun setupFellesMocker(
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        vurdering: Vurdering? = null,
        vurderingListe: List<Vurdering> = listOfNotNull(vurdering),
        forslag: List<Forslag> = emptyList(),
        soktInnDato: LocalDate? = null,
    ) {
        val ansatteCache: GenericCache<NavAnsatt> = GenericCache(
            cacheName = "navAnsatte",
            items = listOf(navAnsatt),
            idSelector = { it.id },
        )
        val enheterCache: GenericCache<NavEnhet> = GenericCache(
            cacheName = "navEnheter",
            items = listOf(navEnhet),
            idSelector = { it.id },
        )

        coEvery { navAnsattService.hentNavAnsatteForDeltakere(any()) } returns ansatteCache
        coEvery { navEnhetService.hentNavEnheterForDeltakere(any()) } returns enheterCache
        every { arrangorService.getArrangorNavn(any(), any()) } returns "~arrangor-navn~"
        every { vurderingRepository.getSisteVurderingForDeltakere(any()) } answers {
            val ider = firstArg<Set<UUID>>()
            val sistePerDeltaker = vurderingListe
                .filter { it.deltakerId in ider }
                .groupBy { it.deltakerId }
                .mapValues { (_, v) -> v.maxBy { it.gyldigFra } }
            sistePerDeltaker
        }
        every { forslagRepository.getVenterPaSvarForDeltakere(any()) } answers {
            val ider = firstArg<Set<UUID>>()
            forslag
                .filter { it.deltakerId in ider && it.status is Forslag.Status.VenterPaSvar }
                .groupBy { it.deltakerId }
        }
        every { deltakerHistorikkService.getSoktInnDatoer(any()) } answers {
            firstArg<Set<UUID>>().associateWith { soktInnDato }
        }
        every { deltakerLaaseService.erLaastForEndringerForDeltakere(any()) } answers {
            firstArg<List<no.nav.amt.deltaker.model.Deltaker>>().associate { it.id to false }
        }
        coEvery { distribusjonClient.digitalBruker(any()) } returns true
    }

    private fun lagForslag(
        deltakerId: UUID,
        status: Forslag.Status,
    ) = Forslag(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        opprettet = LocalDateTime.now(),
        begrunnelse = null,
        endring = Forslag.ForlengDeltakelse(LocalDate.now().plusWeeks(2)),
        status = status,
    )

    private fun lagVurdering(
        deltakerId: UUID,
        gyldigFra: LocalDateTime,
    ) = Vurdering(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        gyldigFra = gyldigFra,
        vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
        begrunnelse = null,
    )
}
