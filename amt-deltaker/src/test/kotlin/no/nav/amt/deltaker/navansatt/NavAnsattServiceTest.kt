package no.nav.amt.deltaker.navansatt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class NavAnsattServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()

    val mockPersonServiceClient = mockk<AmtPersonServiceClient>()

    val navEnhetService = NavEnhetService(
        repository = navEnhetRepository,
        amtPersonServiceClient = mockPersonServiceClient,
    )

    private val navAnsattService = NavAnsattService(
        repository = navAnsattRepository,
        amtPersonServiceClient = mockPersonServiceClient,
        navEnhetService = navEnhetService,
    )

    private val navEnhet = lagNavEnhet()
    private val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        navEnhetRepository.upsert(navEnhet)
        navAnsattRepository.upsert(navAnsatt)
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes i db - henter fra db`() = runTest {
        val navAnsattFraDb = navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)

        navAnsattFraDb shouldBe navAnsatt
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes ikke i db - henter fra personservice og lagrer`() = runTest {
        val navAnsattResponse = lagNavAnsatt(navEnhetId = navEnhet.id)

        coEvery { mockPersonServiceClient.hentNavEnhet(navAnsattResponse.navEnhetId.shouldNotBeNull()) } returns navEnhet
        coEvery { mockPersonServiceClient.hentNavAnsatt(navAnsattResponse.navIdent) } returns navAnsattResponse

        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

        navAnsatt shouldBe navAnsattResponse
        navAnsattRepository.get(navAnsattResponse.id) shouldBe navAnsattResponse
    }

    @Test
    fun `oppdaterNavAnsatt - navansatt finnes - blir oppdatert`() = runTest {
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        navAnsattService.oppdaterNavAnsatt(oppdatertNavAnsatt)

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Nested
    inner class HentNavAnsatteForDeltakerTests {
        @Test
        fun `1 Nav-ansatt finnes i db, 2 ansatte finnes ikke i db, returnerer Nav-ansatte`() = runTest {
            // Arrange
            val vedtakOpprettetAv = lagNavAnsatt(navEnhetId = navEnhet.id)
            val vedtakSistEndretAv = lagNavAnsatt(navEnhetId = navEnhet.id)
            val extraNavAnsattId = UUID.randomUUID()

            val tempDeltakerInTest = lagDeltaker(
                navBruker = lagNavBruker(
                    navVeilederId = navAnsatt.id,
                    navEnhetId = navEnhet.id,
                ),
            )

            val vedtak = lagVedtak(
                deltakerId = tempDeltakerInTest.id,
                deltakerVedVedtak = tempDeltakerInTest,
                opprettetAv = vedtakOpprettetAv,
                sistEndretAv = vedtakSistEndretAv,
                opprettetAvEnhet = navEnhet,
                fattet = null,
            )

            val deltakerInTest = tempDeltakerInTest.copy(
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )
            TestRepository.insert(deltakerInTest)

            coEvery { mockPersonServiceClient.hentNavAnsatt(vedtakOpprettetAv.id) } returns vedtakOpprettetAv
            coEvery { mockPersonServiceClient.hentNavAnsatt(vedtakSistEndretAv.id) } returns vedtakSistEndretAv
            coEvery {
                mockPersonServiceClient.hentNavAnsatt(extraNavAnsattId)
            } returns lagNavAnsatt(id = extraNavAnsattId, navEnhetId = navEnhet.id)

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltaker(
                deltaker = deltakerInTest,
                additionalIds = setOf(extraNavAnsattId),
            )

            // Assert
            ansatte.getOrThrow(navAnsatt.id) shouldBe navAnsattRepository.get(navAnsatt.id)
            ansatte.getOrThrow(vedtakOpprettetAv.id) shouldBe vedtakOpprettetAv
            ansatte.getOrThrow(vedtakSistEndretAv.id) shouldBe vedtakSistEndretAv

            coVerify(exactly = 3) { mockPersonServiceClient.hentNavAnsatt(any<UUID>()) }
        }
    }

    @Nested
    inner class HentNavAnsatteForDeltakereTests {
        @Test
        fun `tom liste med deltakere - returnerer tom cache uten oppslag`() = runTest {
            // Arrange — ingen deltakere

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltakere(emptyList())

            // Assert
            // ingen ID-er => ingen oppslag mot personservice
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavAnsatt(any<UUID>()) }
            // verifiser at cachen er tom ved at oppslag på en tilfeldig id kaster
            shouldThrow<NoSuchElementException> { ansatte.getOrThrow(UUID.randomUUID()) }
        }

        @Test
        fun `alle Nav-ansatte finnes i db - henter kun fra db, ingen kall til personservice`() = runTest {
            // Arrange
            val veileder2 = lagNavAnsatt(navEnhetId = navEnhet.id).also { navAnsattRepository.upsert(it) }

            val deltaker1 = lagDeltaker(navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id))
            val deltaker2 = lagDeltaker(navBruker = lagNavBruker(navVeilederId = veileder2.id, navEnhetId = navEnhet.id))

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltakere(listOf(deltaker1, deltaker2))

            // Assert
            ansatte.getOrThrow(navAnsatt.id) shouldBe navAnsatt
            ansatte.getOrThrow(veileder2.id) shouldBe veileder2
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavAnsatt(any<UUID>()) }
        }

        @Test
        fun `manglende Nav-ansatte hentes fra personservice og lagres`() = runTest {
            // Arrange
            val manglendeVeileder = lagNavAnsatt(navEnhetId = navEnhet.id)

            val deltakerMedKjentVeileder = lagDeltaker(
                navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            )
            val deltakerMedUkjentVeileder = lagDeltaker(
                navBruker = lagNavBruker(navVeilederId = manglendeVeileder.id, navEnhetId = navEnhet.id),
            )

            coEvery { mockPersonServiceClient.hentNavAnsatt(manglendeVeileder.id) } returns manglendeVeileder

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltakere(
                listOf(deltakerMedKjentVeileder, deltakerMedUkjentVeileder),
            )

            // Assert
            ansatte.getOrThrow(navAnsatt.id) shouldBe navAnsatt
            ansatte.getOrThrow(manglendeVeileder.id) shouldBe manglendeVeileder
            navAnsattRepository.get(manglendeVeileder.id) shouldBe manglendeVeileder
            coVerify(exactly = 1) { mockPersonServiceClient.hentNavAnsatt(manglendeVeileder.id) }
        }

        @Test
        fun `samme veileder paa flere deltakere - dedupliseres til ett oppslag`() = runTest {
            // Arrange
            val deltaker1 = lagDeltaker(navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id))
            val deltaker2 = lagDeltaker(navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id))
            val deltaker3 = lagDeltaker(navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id))

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltakere(listOf(deltaker1, deltaker2, deltaker3))

            // Assert
            ansatte.getOrThrow(navAnsatt.id) shouldBe navAnsatt
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavAnsatt(any<UUID>()) }
        }

        @Test
        fun `inkluderer ansatte fra vedtaksinformasjon`() = runTest {
            // Arrange
            val opprettetAv = lagNavAnsatt(navEnhetId = navEnhet.id).also { navAnsattRepository.upsert(it) }
            val sistEndretAv = lagNavAnsatt(navEnhetId = navEnhet.id).also { navAnsattRepository.upsert(it) }

            val tempDeltaker = lagDeltaker(
                navBruker = lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            )
            val vedtak = lagVedtak(
                deltakerId = tempDeltaker.id,
                deltakerVedVedtak = tempDeltaker,
                opprettetAv = opprettetAv,
                sistEndretAv = sistEndretAv,
                opprettetAvEnhet = navEnhet,
                fattet = null,
            )
            val deltaker = tempDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())

            // Act
            val ansatte = navAnsattService.hentNavAnsatteForDeltakere(listOf(deltaker))

            // Assert
            ansatte.getOrThrow(navAnsatt.id) shouldBe navAnsatt
            ansatte.getOrThrow(opprettetAv.id) shouldBe opprettetAv
            ansatte.getOrThrow(sistEndretAv.id) shouldBe sistEndretAv
            coVerify(exactly = 0) { mockPersonServiceClient.hentNavAnsatt(any<UUID>()) }
        }
    }
}
