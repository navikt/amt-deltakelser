package no.nav.amt.deltaker.bff.innbygger

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagOppfolgingsperiode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class NavBrukerConsumerTest {
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val navAnsattService = NavAnsattService(
        repository = NavAnsattRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(
        repository = NavEnhetRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockk(relaxed = true),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(relaxed = true),
    )
    private val navBrukerService = NavBrukerService(
        amtPersonServiceClient = amtPersonServiceClient,
        repository = NavBrukerRepository(),
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )

    private var pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = navBrukerService,
        navEnhetService = navEnhetService,
        paameldingClient = mockk(relaxed = true),
    )

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() = runTest {
        val navBruker = lagNavBruker()
        val navVeileder = lagNavAnsatt(navBruker.navVeilederId!!)
        val navEnhet = lagNavEnhet(navBruker.navEnhetId!!)
        val navBrukerConsumer = NavBrukerConsumer(navBrukerService, pameldingService)

        coEvery { amtPersonServiceClient.hentNavAnsatt(navVeileder.id) } returns navVeileder
        coEvery { amtPersonServiceClient.hentNavEnhet(navEnhet.id) } returns navEnhet

        navBrukerConsumer.consume(navBruker.personId, navBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe navBruker
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - upserter`() = runTest {
        val navBruker = lagNavBruker()
        val navEnhet = lagNavEnhet(navBruker.navEnhetId!!)
        navEnhetRepository.upsert(navEnhet)
        TestRepository.insert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        val navBrukerConsumer = NavBrukerConsumer(navBrukerService, pameldingService)

        navBrukerConsumer.consume(navBruker.personId, oppdatertNavBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
    }

    @Test
    fun `consumeNavBruker - avsluttet oppfolging - sletter kladd`() = runTest {
        val navBruker = lagNavBruker()
        val navEnhet = lagNavEnhet(navBruker.navEnhetId!!)
        val kladd = TestData.lagDeltakerKladd(navBruker = navBruker)
        navEnhetRepository.upsert(navEnhet)
        TestRepository.insert(kladd)

        val oppdatertNavBruker = navBruker.copy(
            innsatsgruppe = null,
            oppfolgingsperioder = listOf(
                lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(1),
                    sluttdato = LocalDateTime.now().minusDays(2),
                ),
            ),
        )

        val navBrukerConsumer = NavBrukerConsumer(navBrukerService, pameldingService)

        navBrukerConsumer.consume(navBruker.personId, oppdatertNavBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
        deltakerRepository.get(kladd.id).getOrNull() shouldBe null
    }

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()

        private fun NavEnhet.toDto() = NavEnhetDto(
            id,
            enhetsnummer,
            navn,
        )

        private fun NavBruker.toDto(navEnhet: NavEnhet) = NavBrukerDto(
            personId = personId,
            personident = personident,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            navVeilederId = navVeilederId,
            navEnhet = navEnhet.toDto(),
            erSkjermet = erSkjermet,
            adresse = adresse,
            adressebeskyttelse = adressebeskyttelse,
            oppfolgingsperioder = oppfolgingsperioder,
            innsatsgruppe = innsatsgruppe,
            telefon = null,
            epost = null,
        )

        private fun NavBrukerDto.toJSON(): String = objectMapper.writeValueAsString(this)
    }
}
