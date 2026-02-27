package no.nav.amt.deltaker.bff.deltaker.navbruker

import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavBruker
import org.slf4j.LoggerFactory
import java.util.UUID

class NavBrukerService(
    private val amtPersonServiceClient: AmtPersonServiceClient,
    private val repository: NavBrukerRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = repository.get(id)

    suspend fun getOrCreate(personident: String): Result<NavBruker> {
        val brukerResult = repository.get(personident)
        if (brukerResult.isSuccess) return brukerResult

        val bruker = amtPersonServiceClient.hentNavBruker(personident)

        log.info("Oppretter nav-bruker ${bruker.personId}")
        return upsertNavBruker(bruker)
    }

    suspend fun upsert(navBruker: NavBruker) {
        val bruker = repository.get(navBruker.personId).getOrNull()
        if (navBruker != bruker) upsertNavBruker(navBruker)
    }

    suspend fun update(personident: String) {
        val lagretBruker = repository.get(personident).getOrNull()
        val bruker = amtPersonServiceClient.hentNavBruker(personident)

        log.info("Oppdaterte nav-bruker ${bruker.personId} med data fra amt-person-service")
        if (lagretBruker != bruker) repository.upsert(bruker)
    }

    private suspend fun upsertNavBruker(navBruker: NavBruker): Result<NavBruker> {
        navBruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
        navBruker.navEnhetId?.let { navEnhetService.hentEllerOpprettEnhet(it) }

        return repository.upsert(navBruker)
    }
}
