package no.nav.amt.deltaker.bff.navansatt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattServiceTest {
    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(repository = navAnsattRepository, amtPersonServiceClient = mockk())
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class HentNavAnsatt {
        @Test
        fun `skal returnere Nav-ansatt nar den finnes i db`() {
            val navAnsatt = lagNavAnsatt()
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
            val navAnsatt = lagNavAnsatt()
            navAnsattRepository.upsert(navAnsatt)

            val navAnsattFraDb = navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }

        @Test
        fun `Nav-ansatt finnes ikke i db - henter fra personservice og lagrer`() = runTest {
            val navAnsattResponse = lagNavAnsatt()
            coEvery { amtPersonServiceClient.hentNavAnsatt(navAnsattResponse.navIdent) } returns navAnsattResponse

            val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient)

            val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

            navAnsatt shouldBe navAnsattResponse
            navAnsattRepository.get(navAnsattResponse.id) shouldBe navAnsattResponse
        }
    }

    @Test
    fun `oppdaterNavAnsatt - Nav-ansatt finnes - blir oppdatert`() {
        val navAnsatt = lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        navAnsattService.oppdaterNavAnsatt(oppdatertNavAnsatt)

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Test
    fun `slettNavAnsatt - Nav-ansatt blir slettet`() {
        val navAnsatt = lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)

        navAnsattService.slettNavAnsatt(navAnsatt.id)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}
