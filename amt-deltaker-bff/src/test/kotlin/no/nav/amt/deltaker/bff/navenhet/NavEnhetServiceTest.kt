package no.nav.amt.deltaker.bff.navenhet

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class NavEnhetServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val amtPersonServiceClient = mockAmtPersonServiceClient()
    private val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - navenhet finnes i db - henter fra db`() = runTest {
        val navEnhet = TestData.lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navEnhetFraDb = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhet.enhetsnummer)
        navEnhetFraDb shouldBe navEnhet
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - navenhet finnes ikke i db - henter fra personservice og lagrer`() = runTest {
        val navEnhetResponse = TestData.lagNavEnhet()
        MockResponseHandler.addNavEnhetPostResponse(navEnhetResponse)

        val navEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhetResponse.enhetsnummer)

        navEnhet shouldBe navEnhetResponse
        navEnhetRepository.get(navEnhetResponse.enhetsnummer)?.toNavEnhet() shouldBe navEnhetResponse
    }

    @Test
    fun `hentOpprettEllerOppdaterNavEnhet - utdatert navenhet finnes i db - henter fra personservice og oppdaterer`() = runTest {
        val opprinneligNavEnhet = TestData.lagNavEnhet()
        TestRepository.insert(
            navEnhet = opprinneligNavEnhet,
            sistEndret = LocalDateTime.now().minusMonths(2),
        )

        val navEnhetResponse = opprinneligNavEnhet.copy(navn = "Oppdater navn")
        MockResponseHandler.addNavEnhetPostResponse(navEnhetResponse)

        val navEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhetResponse.enhetsnummer)

        navEnhet shouldBe navEnhetResponse
        navEnhetRepository.get(navEnhetResponse.enhetsnummer)?.toNavEnhet() shouldBe navEnhetResponse
    }

    @Test
    fun `hentEnheterForHistorikk - historikk endret av flere ansatte - returnerer alle enheter`() {
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

        val faktiskeEnheter = runBlocking { navEnhetService.hentEnheterForHistorikk(historikk) }
        faktiskeEnheter.size shouldBe enheter.size

        faktiskeEnheter.toList().map { it.second }.containsAll(enheter) shouldBe true
    }

    @Test
    fun `hentEnheterForHistorikk - enhet finnes ikke i database - henter og returnerer enhet`() {
        val deltaker = TestData.lagDeltaker()
        val endring = TestData.lagEndringFraTiltakskoordinator()

        val historikk = listOf(
            DeltakerHistorikk.EndringFraTiltakskoordinator(endring),
        )

        TestRepository.insert(deltaker)
        MockResponseHandler.addNavEnhetGetResponse(TestData.lagNavEnhet(id = endring.endretAvEnhet))

        val faktiskeEnheter = runBlocking { navEnhetService.hentEnheterForHistorikk(historikk) }
        faktiskeEnheter.size shouldBe 1

        faktiskeEnheter[endring.endretAvEnhet] shouldNotBe null
    }
}
