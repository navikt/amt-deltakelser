package no.nav.amt.deltaker.navansatt

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.utils.GenericCache
import org.slf4j.LoggerFactory
import java.util.UUID

class NavAnsattService(
    private val repository: NavAnsattRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavAnsatt(navIdent: String): NavAnsatt = repository.get(navIdent) ?: run {
        log.info("Fant ikke Nav-ansatt med ident $navIdent, henter fra amt-person-service")
        oppdaterNavAnsatt(amtPersonServiceClient.hentNavAnsatt(navIdent))
    }

    suspend fun hentEllerOpprettNavAnsatt(id: UUID): NavAnsatt = repository.get(id) ?: run {
        log.info("Fant ikke Nav-ansatt med id $id, henter fra amt-person-service")
        oppdaterNavAnsatt(amtPersonServiceClient.hentNavAnsatt(id))
    }

    fun getMany(ider: Set<UUID>): List<NavAnsatt> = repository.getManyById(ider)

    suspend fun oppdaterNavAnsatt(navAnsatt: NavAnsatt): NavAnsatt {
        navAnsatt.navEnhetId?.also { navEnhetService.hentEllerOpprettNavEnhet(it) }
        return repository.upsert(navAnsatt)
    }

    suspend fun hentNavAnsatteForDeltaker(
        deltaker: Deltaker,
        additionalIds: Set<UUID> = emptySet(),
    ): GenericCache<NavAnsatt> {
        val navAnsattIdSet = setOfNotNull(
            deltaker.navBruker.navVeilederId,
            deltaker.vedtaksinformasjon?.opprettetAv,
            deltaker.vedtaksinformasjon?.sistEndretAv,
        ).plus(additionalIds)

        // hent Nav-ansatte fra db
        val fraDb = repository.getManyById(navAnsattIdSet)

        // hent Nav-ansatte som mangler i db fra amt-person-service
        val manglendeIder = navAnsattIdSet - fraDb.map { it.id }.toSet()
        val fraTjeneste = manglendeIder.map {
            val navAnsatt = amtPersonServiceClient.hentNavAnsatt(it)
            oppdaterNavAnsatt(navAnsatt)
        }

        return GenericCache("navAnsatte", fraDb + fraTjeneste, idSelector = { it.id })
    }
}
