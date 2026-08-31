package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import java.util.UUID

data class OpplaringKategoriseringValgRequest(
    val representerer: OpplaringKategoriseringType,
    val valgteIder: Set<UUID>,
)
