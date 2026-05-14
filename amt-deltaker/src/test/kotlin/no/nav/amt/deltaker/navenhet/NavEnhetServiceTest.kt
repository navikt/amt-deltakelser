package no.nav.amt.deltaker.navenhet

import io.kotest.assertions.throwables.shouldThrow
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

    @Nested
    inner class HentNavEnheterForDeltakereTests {
        val mockPersonServiceClient = mockk<AmtPersonServiceClient>()

        val navEnhetService = NavEnhetService(
            repository = navEnhetRepository,
            amtPersonServiceClient = mockPersonServiceClient,
        )

        @Test
        fun `tom liste med deltakere - returnerer tom cache uten oppslag`() = runTest {
            // Arrange — ingen deltakere

            // Act
            val enheter = navEnhetService.hentNavEnheterForDeltakere(emptyList())

            // Assert
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavEnhet(any<UUID>()) }
            shouldThrow<NoSuchElementException> { enheter.getOrThrow(UUID.randomUUID()) }
        }

        @Test
        fun `alle Nav-enheter finnes i db - henter kun fra db, ingen kall til personservice`() = runTest {
            // Arrange
            val enhet1 = lagNavEnhet().also { navEnhetRepository.upsert(it) }
            val enhet2 = lagNavEnhet().also { navEnhetRepository.upsert(it) }

            val deltaker1 = lagDeltaker(navBruker = lagNavBruker(navEnhetId = enhet1.id))
            val deltaker2 = lagDeltaker(navBruker = lagNavBruker(navEnhetId = enhet2.id))

            // Act
            val enheter = navEnhetService.hentNavEnheterForDeltakere(listOf(deltaker1, deltaker2))

            // Assert
            enheter.getOrThrow(enhet1.id) shouldBe enhet1
            enheter.getOrThrow(enhet2.id) shouldBe enhet2
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavEnhet(any<UUID>()) }
        }

        @Test
        fun `manglende Nav-enheter hentes fra personservice og lagres`() = runTest {
            // Arrange
            val kjentEnhet = lagNavEnhet().also { navEnhetRepository.upsert(it) }
            val manglendeEnhet = lagNavEnhet()

            val deltakerMedKjentEnhet = lagDeltaker(navBruker = lagNavBruker(navEnhetId = kjentEnhet.id))
            val deltakerMedUkjentEnhet = lagDeltaker(navBruker = lagNavBruker(navEnhetId = manglendeEnhet.id))

            coEvery { mockPersonServiceClient.hentNavEnhet(manglendeEnhet.id) } returns manglendeEnhet

            // Act
            val enheter = navEnhetService.hentNavEnheterForDeltakere(
                listOf(deltakerMedKjentEnhet, deltakerMedUkjentEnhet),
            )

            // Assert
            enheter.getOrThrow(kjentEnhet.id) shouldBe kjentEnhet
            enheter.getOrThrow(manglendeEnhet.id) shouldBe manglendeEnhet
            navEnhetRepository.get(manglendeEnhet.id) shouldBe manglendeEnhet
            coVerify(exactly = 1) { mockPersonServiceClient.hentNavEnhet(manglendeEnhet.id) }
        }

        @Test
        fun `samme enhet paa flere deltakere - dedupliseres til ett oppslag`() = runTest {
            // Arrange
            val enhet = lagNavEnhet().also { navEnhetRepository.upsert(it) }

            val deltaker1 = lagDeltaker(navBruker = lagNavBruker(navEnhetId = enhet.id))
            val deltaker2 = lagDeltaker(navBruker = lagNavBruker(navEnhetId = enhet.id))
            val deltaker3 = lagDeltaker(navBruker = lagNavBruker(navEnhetId = enhet.id))

            // Act
            val enheter = navEnhetService.hentNavEnheterForDeltakere(listOf(deltaker1, deltaker2, deltaker3))

            // Assert
            enheter.getOrThrow(enhet.id) shouldBe enhet
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavEnhet(any<UUID>()) }
        }

        @Test
        fun `vedtaksinformasjon-enheter utelates - kun navBruker navEnhetId hentes`() = runTest {
            // Arrange — tiltakskoordinator-flyten trenger ikke vedtak-enheter, så bulk-metoden
            // henter kun navBruker.navEnhetId for å unngå unødvendige oppslag.
            val brukerEnhet = lagNavEnhet().also { navEnhetRepository.upsert(it) }
            val opprettetAvEnhet = lagNavEnhet().also { navEnhetRepository.upsert(it) }
            val sistEndretAvEnhet = lagNavEnhet().also { navEnhetRepository.upsert(it) }

            val tempDeltaker = lagDeltaker(navBruker = lagNavBruker(navEnhetId = brukerEnhet.id))
            val vedtak = lagVedtak(
                deltakerId = tempDeltaker.id,
                deltakerVedVedtak = tempDeltaker,
                opprettetAvEnhet = opprettetAvEnhet,
                sistEndretAvEnhet = sistEndretAvEnhet,
            )
            val deltaker = tempDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())

            // Act
            val enheter = navEnhetService.hentNavEnheterForDeltakere(listOf(deltaker))

            // Assert
            enheter.getOrThrow(brukerEnhet.id) shouldBe brukerEnhet
            shouldThrow<NoSuchElementException> { enheter.getOrThrow(opprettetAvEnhet.id) }
            shouldThrow<NoSuchElementException> { enheter.getOrThrow(sistEndretAvEnhet.id) }
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavEnhet(any<UUID>()) }
        }
    }
}
