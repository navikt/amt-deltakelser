package no.nav.amt.deltaker.bff.arrangor

import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor

class ArrangorService(
    private val repository: ArrangorRepository,
    private val amtArrangorClient: AmtArrangorClient,
) {
    suspend fun hentArrangor(orgnr: String): Arrangor {
        return repository.get(orgnr) ?: return opprettArrangor(orgnr)
    }

    private suspend fun opprettArrangor(orgnr: String): Arrangor {
        val arrangor = amtArrangorClient.hentArrangor(orgnr)

        arrangor.overordnetArrangor?.let { repository.upsert(it) }
        repository.upsert(arrangor.toModel())

        return arrangor.toModel()
    }
}
