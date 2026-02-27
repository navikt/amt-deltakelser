package no.nav.amt.deltaker.navenhet

import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavEnhet
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavEnhet(enhetsnummer: String): NavEnhet {
        repository.get(enhetsnummer)?.let { return it }

        log.info("Fant ikke Nav-enhet med nummer $enhetsnummer, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(enhetsnummer)
        return repository.upsert(navEnhet)
    }

    suspend fun hentEllerOpprettNavEnhet(id: UUID): NavEnhet {
        repository.get(id)?.let { return it }

        log.info("Fant ikke Nav-enhet med id $id, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(id)
        return repository.upsert(navEnhet)
    }

    fun getEnheter(ider: Set<UUID>) = repository.getMany(ider).associateBy { it.id }
}
