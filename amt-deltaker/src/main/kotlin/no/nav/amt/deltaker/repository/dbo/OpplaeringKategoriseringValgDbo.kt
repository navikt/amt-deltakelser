package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import java.util.UUID

data class OpplaeringKategoriseringValgDbo(
    val representerer: OpplaringKategoriseringType,
    val kodeverkId: UUID,
    val tekst: String,
)
