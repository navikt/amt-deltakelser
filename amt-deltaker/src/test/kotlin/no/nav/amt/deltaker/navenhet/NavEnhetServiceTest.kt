package no.nav.amt.deltaker.navenhet

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class NavEnhetServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetResponse = lagNavEnhet()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() = clearAllMocks()

    @Test
    fun `hentEllerOpprettNavEnhet - navenhet finnes i db - henter fra db`() {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)
        val navEnhetService = NavEnhetService(navEnhetRepository, mockk())

        runTest {
            val navEnhetFraDb = navEnhetService.hentEllerOpprettNavEnhet(navEnhet.enhetsnummer)
            navEnhetFraDb shouldBe navEnhet
        }
    }

    @Test
    fun `hentEllerOpprettNavEnhet - navenhet finnes ikke i db - henter fra personservice og lagrer`() = runTest {
        // Arrange
        val amtPersonServiceClient: AmtPersonServiceClient = mockk()
        val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)

        coEvery { amtPersonServiceClient.hentNavEnhet(navEnhetResponse.enhetsnummer) } returns navEnhetResponse

        // Act
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(navEnhetResponse.enhetsnummer)

        // Assert
        navEnhet shouldBe navEnhetResponse
        navEnhetRepository.get(navEnhetResponse.enhetsnummer) shouldBe navEnhetResponse
    }

    @Nested
    inner class HentNavEnheterForDeltakerTests {
        val mockPersonServiceClient = mockk<AmtPersonServiceClient>()

        val navEnhetService = NavEnhetService(
            repository = navEnhetRepository,
            amtPersonServiceClient = mockPersonServiceClient,
        )

        @Test
        fun `1 Nav-enhet finnes i db, 2 enheter finnes ikke i db, returnerer Nav-enheter`() = runTest {
            // Arrange
            val navEnhetIdInTest = UUID.randomUUID()
            val vedtakOpprettetAvEnhet = lagNavEnhet()
            val vedtakSistEndretAvEnhet = lagNavEnhet()
            val extraNavEnhetId = UUID.randomUUID()

            val tempDeltakerInTest = lagDeltaker(
                navBruker = lagNavBruker(navEnhetId = navEnhetIdInTest),
            )

            val vedtak = lagVedtak(
                deltakerId = tempDeltakerInTest.id,
                deltakerVedVedtak = tempDeltakerInTest,
                opprettetAvEnhet = vedtakOpprettetAvEnhet,
                sistEndretAvEnhet = vedtakSistEndretAvEnhet,
            )

            val deltakerInTest = tempDeltakerInTest.copy(
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )
            TestRepository.insert(deltakerInTest)

            coEvery { mockPersonServiceClient.hentNavEnhet(vedtakOpprettetAvEnhet.id) } returns vedtakOpprettetAvEnhet
            coEvery { mockPersonServiceClient.hentNavEnhet(vedtakSistEndretAvEnhet.id) } returns vedtakSistEndretAvEnhet
            coEvery { mockPersonServiceClient.hentNavEnhet(extraNavEnhetId) } returns lagNavEnhet(id = extraNavEnhetId)

            // Act
            val navEnheter = navEnhetService.hentNavEnheterForDeltaker(
                deltaker = deltakerInTest,
                additionalIds = setOf(extraNavEnhetId),
            )

            // Assert
            navEnheter.getOrThrow(navEnhetIdInTest) shouldBe navEnhetRepository.get(navEnhetIdInTest)
            navEnheter.getOrThrow(vedtakOpprettetAvEnhet.id) shouldBe vedtakOpprettetAvEnhet
            navEnheter.getOrThrow(vedtakSistEndretAvEnhet.id) shouldBe vedtakSistEndretAvEnhet

            coVerify(exactly = 3) { mockPersonServiceClient.hentNavEnhet(any<UUID>()) }
        }
    }
}
