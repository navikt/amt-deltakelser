package no.nav.amt.deltaker.navbruker

import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavBruker
import org.slf4j.LoggerFactory

class NavBrukerService(
    private val repository: NavBrukerRepository,
    private val personServiceClient: AmtPersonServiceClient,
    private val enhetService: NavEnhetService,
    private val ansattService: NavAnsattService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun get(personIdent: String): Result<NavBruker> {
        val brukerResult = repository.get(personIdent)
        if (brukerResult.isSuccess) {
            // workaround for deltakere som ikke har fått lastet innsatsgruppe ennå
            val bruker = brukerResult.getOrThrow()
            if (bruker.innsatsgruppe != null) {
                return brukerResult
            }
        }
        val bruker = try {
            personServiceClient.hentNavBruker(personIdent)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        bruker.navEnhetId?.let { enhetService.hentEllerOpprettNavEnhet(it) }
        bruker.navVeilederId?.let { ansattService.hentEllerOpprettNavAnsatt(it) }

        log.info("Oppretter nav-bruker ${bruker.personId}")
        return repository.upsert(bruker)
    }
}
