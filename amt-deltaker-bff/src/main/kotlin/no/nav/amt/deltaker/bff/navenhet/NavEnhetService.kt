package no.nav.amt.deltaker.bff.navenhet

import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavEnhet
import java.util.UUID

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    suspend fun hentEllerOpprettEnhet(id: UUID): NavEnhet {
        repository.get(id)?.let { return it.toNavEnhet() }

        val navEnhet = amtPersonServiceClient.hentNavEnhet(id)
        return upsert(navEnhet)
    }

    fun upsert(enhet: NavEnhet) = repository.upsert(enhet).toNavEnhet()
}
