package no.nav.tiltaksarrangor.api.request

import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.util.UUID

data class ForslagRequest(
    val forslagIder: List<UUID>,
    val dryRun: Boolean = true,
    val status: Forslag.Status? = null,
)
