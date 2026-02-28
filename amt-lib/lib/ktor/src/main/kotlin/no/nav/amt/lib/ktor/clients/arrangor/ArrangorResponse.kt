package no.nav.amt.lib.ktor.clients.arrangor

import no.nav.amt.lib.models.deltaker.Arrangor
import java.util.UUID

data class ArrangorResponse(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangor: Arrangor?,
) {
    fun toModel() =
        Arrangor(
            id = id,
            navn = navn,
            organisasjonsnummer = organisasjonsnummer,
            overordnetArrangorId = overordnetArrangor?.id,
        )
}
