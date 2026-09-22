package no.nav.amt.deltaker.innbygger

import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhetDto
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavBrukerConsumerTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val navBrukerRepository = NavBrukerRepository()
    private val deltakerService = mockk<DeltakerService>()
    private val kladdService = mockk<KladdService>()

    private val navEnhet = lagNavEnhet()
    private val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        fun lagNavBrukerDto(
            navBruker: NavBruker,
            navEnhet: NavEnhet,
        ) = NavBrukerDto(
            personId = navBruker.personId,
            personident = navBruker.personident,
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            navVeilederId = navBruker.navVeilederId,
            navEnhet = lagNavEnhetDto(navEnhet),
            telefon = navBruker.telefon,
            epost = navBruker.epost,
            erSkjermet = navBruker.erSkjermet,
            adresse = navBruker.adresse,
            adressebeskyttelse = navBruker.adressebeskyttelse,
            oppfolgingsperioder = navBruker.oppfolgingsperioder,
            innsatsgruppe = navBruker.innsatsgruppe,
        )
    }

    @BeforeEach
    fun setup() {
        clearMocks(deltakerService, kladdService)

        navEnhetRepository.upsert(navEnhet)
        navAnsattRepository.upsert(navAnsatt)

        every {
            deltakerService.produserDeltakereForPerson(any(), any(), any())
        } just Runs
        every { kladdService.slettKladd(any()) } just Runs
    }

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(navBruker, navEnhet)),
        )

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - ulik personident - upserter - publiserer v1 og v2`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker", personident = randomIdent())

        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(oppdatertNavBruker, navEnhet)),
        )

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
        coVerify { deltakerService.produserDeltakereForPerson(oppdatertNavBruker.personident, true) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - lik personident - upserter - publiserer kun v2`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(oppdatertNavBruker, navEnhet)),
        )

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker

        verify {
            deltakerService.produserDeltakereForPerson(
                personident = oppdatertNavBruker.personident,
                publiserTilDeltakerV1 = false,
                publiserTilDeltakerEksternV1 = false,
            )
        }
    }

    @Test
    fun `consumeNavBruker - ny navBruker, enhet mangler - henter enhet og lagrer`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(navBruker, navEnhet)),
        )

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker, ingen endringer - republiserer ikke deltakere`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(navBruker, navEnhet)),
        )

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify(exactly = 0) { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }

    @Test
    fun `consumeNavBruker - mangler innsatsgruppe - sletter bare kladder`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id, innsatsgruppe = null)
        navBrukerRepository.upsert(navBruker)

        val kladd = lagDeltaker(status = lagDeltakerStatus(statusType = DeltakerStatus.Type.KLADD))
        val aktiv = lagDeltaker(status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR))

        every { deltakerService.getFlereForPerson(navBruker.personident) } returns listOf(kladd, aktiv)

        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(navBruker, navEnhet).copy(innsatsgruppe = null)),
        )

        verify(exactly = 1) { kladdService.slettKladd(kladd.id) }
        verify(exactly = 0) { kladdService.slettKladd(aktiv.id) }
        coVerify(exactly = 0) { deltakerService.produserDeltakereForPerson(any()) }
    }

    @Test
    fun `consumeNavBruker - har innsatsgruppe - sletter ikke kladder`() = runTest {
        val navBruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val navBrukerConsumer = NavBrukerConsumer(
            repository = navBrukerRepository,
            navEnhetService = NavEnhetService(
                repository = navEnhetRepository,
                amtPersonServiceClient = mockk(),
            ),
            deltakerService = deltakerService,
            kladdService = kladdService,
        )

        navBrukerConsumer.consume(
            navBruker.personId,
            objectMapper.writeValueAsString(lagNavBrukerDto(navBruker, navEnhet)),
        )

        verify(exactly = 0) { kladdService.slettKladd(any()) }
    }
}
