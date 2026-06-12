package no.nav.amt.deltaker.bff.innbygger

import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavBruker
import org.slf4j.LoggerFactory

class NavBrukerService(
    private val amtPersonServiceClient: AmtPersonServiceClient,
    private val navBrukerRepository: NavBrukerRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getOrCreate(personident: String): Result<NavBruker> {
        val brukerResult = navBrukerRepository.get(personident)
        if (brukerResult.isSuccess) return brukerResult

        val bruker = amtPersonServiceClient.hentNavBruker(personident)

        log.info("Oppretter nav-bruker ${bruker.personId}")
        return upsertNavBruker(bruker)
    }

    suspend fun upsert(navBruker: NavBruker) {
        val bruker = navBrukerRepository.get(navBruker.personId).getOrNull()
        if (navBruker != bruker) upsertNavBruker(navBruker)
    }

    suspend fun update(personident: String) {
        val lagretBruker = navBrukerRepository.get(personident).getOrNull()
        val bruker = amtPersonServiceClient.hentNavBruker(personident)

        log.info("Oppdaterte nav-bruker ${bruker.personId} med data fra amt-person-service")
        if (lagretBruker != bruker) navBrukerRepository.upsert(bruker)
    }

    private suspend fun upsertNavBruker(navBruker: NavBruker): Result<NavBruker> {
        navBruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
        navBruker.navEnhetId?.let { navEnhetService.hentEllerOpprettEnhet(it) }

        return navBrukerRepository.upsert(navBruker)
    }
}
