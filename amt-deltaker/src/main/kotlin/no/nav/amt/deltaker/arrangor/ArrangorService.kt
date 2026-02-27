package no.nav.amt.deltaker.arrangor

import no.nav.amt.deltaker.utils.toTitleCase
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import java.util.UUID

class ArrangorService(
    private val arrangorRepository: ArrangorRepository,
    private val amtArrangorClient: AmtArrangorClient,
) {
    suspend fun hentArrangor(orgnr: String): Arrangor = arrangorRepository.get(orgnr) ?: opprettArrangor(orgnr)

    fun hentArrangor(id: UUID): Arrangor? = arrangorRepository.get(id)

    private suspend fun opprettArrangor(orgnr: String): Arrangor {
        val arrangor = amtArrangorClient.hentArrangor(orgnr)

        arrangor.overordnetArrangor?.let { arrangorRepository.upsert(it) }
        arrangorRepository.upsert(arrangor.toModel())

        return arrangor.toModel()
    }

    fun getArrangorNavn(arrangor: Arrangor): String {
        val arrangor = arrangor.overordnetArrangorId?.let { arrangorRepository.get(it) } ?: arrangor
        return toTitleCase(arrangor.navn)
    }
}
