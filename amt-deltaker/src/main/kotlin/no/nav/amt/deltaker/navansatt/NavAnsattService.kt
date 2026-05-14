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

    suspend fun hentEllerOpprettNavAnsatt(navIdent: String): NavAnsatt {
        repository.get(navIdent)?.let { return it }

        log.info("Fant ikke Nav-ansatt med ident $navIdent, henter fra amt-person-service")
        val navAnsatt = amtPersonServiceClient.hentNavAnsatt(navIdent)
        return oppdaterNavAnsatt(navAnsatt)
    }

    suspend fun hentEllerOpprettNavAnsatt(id: UUID): NavAnsatt {
        repository.get(id)?.let { return it }

        log.info("Fant ikke Nav-ansatt med id $id, henter fra amt-person-service")
        val navAnsatt = amtPersonServiceClient.hentNavAnsatt(id)
        return oppdaterNavAnsatt(navAnsatt)
    }

    fun getMany(ider: Set<UUID>): List<NavAnsatt> = repository.getManyById(ider)

    suspend fun oppdaterNavAnsatt(navAnsatt: NavAnsatt): NavAnsatt {
        navAnsatt.navEnhetId?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }
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

        return hentNavAnsatte(navAnsattIdSet)
    }

    /**
     * Bulk-variant for store kall (f.eks. tiltakskoordinator-lista). Samler alle relevante
     * Nav-ansatt-ID-er fra deltakerne og gjør ett enkelt DB-oppslag i stedet for ett per deltaker.
     */
    suspend fun hentNavAnsatteForDeltakere(deltakere: List<Deltaker>): GenericCache<NavAnsatt> {
        val navAnsattIdSet = deltakere
            .flatMap { deltaker ->
                listOfNotNull(
                    deltaker.navBruker.navVeilederId,
                    deltaker.vedtaksinformasjon?.opprettetAv,
                    deltaker.vedtaksinformasjon?.sistEndretAv,
                )
            }.toSet()

        return hentNavAnsatte(navAnsattIdSet)
    }

    private suspend fun hentNavAnsatte(navAnsattIdSet: Set<UUID>): GenericCache<NavAnsatt> {
        // hent Nav-ansatte fra db
        val navAnsatteFraDb = repository.getManyById(navAnsattIdSet).associateBy { it.id }

        // hent Nav-ansatte som mangler i db fra amt-person-service
        val manglendeNavAnsatte = (navAnsattIdSet - navAnsatteFraDb.keys)
            .map {
                val navAnsatt = amtPersonServiceClient.hentNavAnsatt(it)
                oppdaterNavAnsatt(navAnsatt)
            }.associateBy { it.id }

        return GenericCache("navAnsatte", navAnsatteFraDb + manglendeNavAnsatte)
    }
}
