package no.nav.amt.deltaker.bff.navansatt

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAzureAdClient
import no.nav.amt.deltaker.bff.utils.mockHttpClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class NavAnsattServiceTest {
    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(repository = navAnsattRepository, amtPersonServiceClient = mockk())

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class HentNavAnsatt {
        @Test
        fun `skal returnere Nav-ansatt nar den finnes i db`() {
            val navAnsatt = TestData.lagNavAnsatt()
            navAnsattRepository.upsert(navAnsatt)

            val navAnsattFraDb = navAnsattService.hentNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }

        @Test
        fun `skal kaste exception nar Nav-ansatt ikke finnes i db`() {
            shouldThrow<NoSuchElementException> {
                navAnsattService.hentNavAnsatt("~nav-ident~")
            }
        }
    }

    @Nested
    inner class HentEllerOpprettNavAnsatt {
        @Test
        fun `Nav-ansatt finnes i db - henter fra db`() = runTest {
            val navAnsatt = TestData.lagNavAnsatt()
            navAnsattRepository.upsert(navAnsatt)

            val navAnsattFraDb = navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }

        @Test
        fun `Nav-ansatt finnes ikke i db - henter fra personservice og lagrer`() = runTest {
            val navAnsattResponse = TestData.lagNavAnsatt()
            val httpClient = mockHttpClient(objectMapper.writeValueAsString(navAnsattResponse))
            val amtPersonServiceClient = AmtPersonServiceClient(
                baseUrl = "http://amt-person-service",
                scope = "scope",
                httpClient = httpClient,
                azureAdTokenClient = mockAzureAdClient(),
            )
            val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient)

            val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

            navAnsatt shouldBe navAnsattResponse
            navAnsattRepository.get(navAnsattResponse.id) shouldBe navAnsattResponse
        }
    }

    @Test
    fun `oppdaterNavAnsatt - Nav-ansatt finnes - blir oppdatert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        navAnsattService.oppdaterNavAnsatt(oppdatertNavAnsatt)

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Test
    fun `slettNavAnsatt - Nav-ansatt blir slettet`() {
        val navAnsatt = TestData.lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)

        navAnsattService.slettNavAnsatt(navAnsatt.id)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }

    @Test
    fun `hentAnsatteForDeltaker - deltaker endret av flere ansatte - returnerer alle ansatte`() {
        val deltaker = TestData.lagDeltaker()

        assertSoftly(deltaker.vedtaksinformasjon.shouldNotBeNull()) {
            opprettetAv.shouldNotBeNull()
            sistEndretAv.shouldNotBeNull()

            opprettetAv shouldBe sistEndretAv
            opprettetAv shouldBe deltaker.navBruker.navVeilederId!!
        }

        TestRepository.insert(deltaker)
        val ansattInDb = navAnsattRepository.get(deltaker.vedtaksinformasjon!!.sistEndretAv).shouldNotBeNull()

        val faktiskeAnsatte = navAnsattService.hentAnsatteForDeltaker(deltaker)

        faktiskeAnsatte.size shouldBe 1
        faktiskeAnsatte[ansattInDb.id] shouldBe ansattInDb
    }

    @Test
    fun `hentAnsatteForHistorikk - historikk endret av flere ansatte - returnerer alle ansatte`() {
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

        val ansatte = TestData.lagNavAnsatteForHistorikk(historikk)

        ansatte.forEach { navAnsattRepository.upsert(it) }
        TestRepository.insert(deltaker)

        val faktiskeAnsatte = navAnsattService.hentAnsatteForHistorikk(historikk)
        faktiskeAnsatte.size shouldBe ansatte.size

        faktiskeAnsatte.toList().map { it.second }.containsAll(ansatte) shouldBe true
    }
}
