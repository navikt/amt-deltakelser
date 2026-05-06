package no.nav.amt.deltaker.bff.navenhet

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class NavEnhetServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val navEnhetService = NavEnhetService(
        repository = navEnhetRepository,
        amtPersonServiceClient = amtPersonServiceClient,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - navenhet finnes i db - henter fra db`() = runTest {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navEnhetFraDb = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhet.enhetsnummer)
        navEnhetFraDb shouldBe navEnhet
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - navenhet finnes ikke i db - henter fra personservice og lagrer`() = runTest {
        val navEnhetResponse = lagNavEnhet()
        coEvery { amtPersonServiceClient.hentNavEnhet(navEnhetResponse.enhetsnummer) } returns navEnhetResponse

        val navEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhetResponse.enhetsnummer)

        navEnhet shouldBe navEnhetResponse
        navEnhetRepository.get(navEnhetResponse.enhetsnummer)?.toNavEnhet() shouldBe navEnhetResponse
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - utdatert navenhet finnes i db - henter fra personservice og oppdaterer`() = runTest {
        val opprinneligNavEnhet = lagNavEnhet()
        TestRepository.insert(
            navEnhet = opprinneligNavEnhet,
            sistEndret = LocalDateTime.now().minusMonths(2),
        )

        val navEnhetResponse = opprinneligNavEnhet.copy(navn = "Oppdater navn")
        coEvery { amtPersonServiceClient.hentNavEnhet(navEnhetResponse.enhetsnummer) } returns navEnhetResponse

        val navEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhetResponse.enhetsnummer)

        navEnhet shouldBe navEnhetResponse
        navEnhetRepository.get(navEnhetResponse.enhetsnummer)?.toNavEnhet() shouldBe navEnhetResponse
    }

    @Test
    fun `hentEnheterForHistorikk - historikk endret av flere ansatte - returnerer alle enheter`() = runTest {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDateTime.now(),
            fattetAvNav = true,
        )
        val deltakerEndring = TestData.lagDeltakerEndring(deltakerId = deltaker.id)
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Avvist(
                avvistAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                avvist = LocalDateTime.now(),
                begrunnelseFraNav = "Begrunnelse",
            ),
        )

        val historikk = listOf(
            DeltakerHistorikk.Endring(deltakerEndring),
            DeltakerHistorikk.Vedtak(vedtak),
            DeltakerHistorikk.Forslag(forslag),
        )

        val enheter = TestData.lagNavEnheterForHistorikk(historikk)

        enheter.forEach { navEnhetRepository.upsert(it) }
        TestRepository.insert(deltaker)

        val faktiskeEnheter = navEnhetService.hentEnheterForHistorikk(historikk)
        faktiskeEnheter.size shouldBe enheter.size

        faktiskeEnheter.toList().map { it.second }.containsAll(enheter) shouldBe true
    }

    @Test
    fun `hentEnheterForHistorikk - enhet finnes ikke i database - henter og returnerer enhet`() = runTest {
        val deltaker = TestData.lagDeltaker()
        val endring = TestData.lagEndringFraTiltakskoordinator()

        val historikk = listOf(
            DeltakerHistorikk.EndringFraTiltakskoordinator(endring),
        )

        TestRepository.insert(deltaker)
        coEvery {
            amtPersonServiceClient.hentNavEnhet(endring.endretAvEnhet)
        } returns lagNavEnhet(id = endring.endretAvEnhet)

        val faktiskeEnheter = navEnhetService.hentEnheterForHistorikk(historikk)
        faktiskeEnheter.size shouldBe 1

        faktiskeEnheter[endring.endretAvEnhet] shouldNotBe null
    }
}
