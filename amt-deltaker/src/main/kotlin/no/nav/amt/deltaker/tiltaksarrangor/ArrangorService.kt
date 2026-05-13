package no.nav.amt.deltaker.tiltaksarrangor

import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.toTitleCase
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

    fun getArrangorNavn(
        arrangor: Arrangor,
        gjennomforingstype: GjennomforingType,
    ): String {
        val navn = if (gjennomforingstype == GjennomforingType.Enkeltplass) {
            arrangor.navn
        } else {
            arrangor.overordnetArrangorId?.let { arrangorRepository.get(it)?.navn } ?: arrangor.navn
        }
        return navn.toTitleCase()
    }
}
