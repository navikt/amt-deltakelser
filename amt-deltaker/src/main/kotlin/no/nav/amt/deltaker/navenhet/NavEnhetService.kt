package no.nav.amt.deltaker.navenhet

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavEnhet(enhetsnummer: String): NavEnhet = repository.get(enhetsnummer) ?: run {
        log.info("Fant ikke Nav-enhet med nummer $enhetsnummer, henter fra amt-person-service")
        repository.upsert(amtPersonServiceClient.hentNavEnhet(enhetsnummer))
    }

    suspend fun hentEllerOpprettNavEnhet(id: UUID): NavEnhet = repository.get(id) ?: run {
        log.info("Fant ikke Nav-enhet med id $id, henter fra amt-person-service")
        repository.upsert(amtPersonServiceClient.hentNavEnhet(id))
    }

    fun getEnheter(ider: Set<UUID>) = repository.getMany(ider).associateBy { it.id }

    suspend fun hentNavEnheterForDeltaker(
        deltaker: Deltaker,
        additionalIds: Set<UUID> = emptySet(),
    ): GenericCache<NavEnhet> {
        val navEnhetIdSet = setOfNotNull(
            deltaker.navBruker.navEnhetId,
            deltaker.vedtaksinformasjon?.opprettetAvEnhet,
            deltaker.vedtaksinformasjon?.sistEndretAvEnhet,
        ).plus(additionalIds)

        return hentNavEnheter(navEnhetIdSet)
    }

    /**
     * Bulk-variant for store kall (f.eks. tiltakskoordinator-lista). Henter kun enhetene som
     * trengs til å rendre `navBruker.navEnhet` for hver deltaker — vedtak-enheter
     * (`opprettetAvEnhet`/`sistEndretAvEnhet`) utelates fordi tiltakskoordinator-responsen alltid
     * setter `vedtaksinformasjon = null`. Gjør ett DB-oppslag for hele settet av ID-er i stedet
     * for ett per deltaker.
     */
    suspend fun hentNavEnheterForDeltakere(deltakere: List<Deltaker>): GenericCache<NavEnhet> {
        val navEnhetIdSet = deltakere
            .mapNotNull { it.navBruker.navEnhetId }
            .toSet()

        return hentNavEnheter(navEnhetIdSet)
    }

    private suspend fun hentNavEnheter(navEnhetIdSet: Set<UUID>): GenericCache<NavEnhet> {
        val enheterFraDb = repository.getMany(navEnhetIdSet).associateBy { it.id }

        // hent Nav-enheter som mangler i db fra amt-person-service
        val manglendeNavEnheter = (navEnhetIdSet - enheterFraDb.keys)
            .map {
                val navEnhet = amtPersonServiceClient.hentNavEnhet(it)
                repository.upsert(navEnhet)
            }.associateBy { it.id }

        return GenericCache(
            cacheName = "navEnheter",
            itemMap = enheterFraDb + manglendeNavEnheter,
        )
    }
}
