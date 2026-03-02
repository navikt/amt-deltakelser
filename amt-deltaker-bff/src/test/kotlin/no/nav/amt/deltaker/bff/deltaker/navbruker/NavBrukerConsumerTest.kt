package no.nav.amt.deltaker.bff.deltaker.navbruker

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAmtDeltakerClient
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.deltaker.bff.utils.mockPaameldingClient
import no.nav.amt.deltaker.bff.utils.toDto
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class NavBrukerConsumerTest {
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClient())
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClient())
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockAmtDeltakerClient(),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(relaxed = true),
    )
    private val navBrukerService: NavBrukerService =
        NavBrukerService(mockAmtPersonServiceClient(), NavBrukerRepository(), navAnsattService, navEnhetService)

    private var pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = navBrukerService,
        navEnhetService = navEnhetService,
        paameldingClient = mockPaameldingClient(),
    )

    private val sut = NavBrukerConsumer(navBrukerService, pameldingService, deltakerRepository)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() = runTest {
        val navBruker = TestData.lagNavBruker()
        val navVeileder = TestData.lagNavAnsatt(navBruker.navVeilederId!!)
        val navEnhet = TestData.lagNavEnhet(navBruker.navEnhetId!!)

        MockResponseHandler.addNavAnsattResponse(navVeileder)
        MockResponseHandler.addNavEnhetGetResponse(navEnhet)

        sut.consume(navBruker.personId, navBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe navBruker
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - upserter`() = runTest {
        val navBruker = TestData.lagNavBruker()
        val navEnhet = TestData.lagNavEnhet(navBruker.navEnhetId!!)
        navEnhetRepository.upsert(navEnhet)
        TestRepository.insert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        sut.consume(navBruker.personId, oppdatertNavBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
    }

    @Test
    fun `consumeNavBruker - avsluttet oppfolging - sletter kladd`() = runTest {
        val navBruker = TestData.lagNavBruker()
        val navEnhet = TestData.lagNavEnhet(navBruker.navEnhetId!!)
        val kladd = TestData.lagDeltakerKladd(navBruker = navBruker)
        navEnhetRepository.upsert(navEnhet)
        TestRepository.insert(kladd)
        MockResponseHandler.addSlettKladdResponse(kladd.id)

        val oppdatertNavBruker = navBruker.copy(
            innsatsgruppe = null,
            oppfolgingsperioder = listOf(
                TestData.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(1),
                    sluttdato = LocalDateTime.now().minusDays(2),
                ),
            ),
        )

        sut.consume(navBruker.personId, oppdatertNavBruker.toDto(navEnhet).toJSON())

        navBrukerService.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
        deltakerRepository.get(kladd.id).getOrNull() shouldBe null
    }
}

fun NavBruker.toDto(navEnhet: NavEnhet) = NavBrukerDto(
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

fun NavBrukerDto.toJSON(): String = objectMapper.writeValueAsString(this)
