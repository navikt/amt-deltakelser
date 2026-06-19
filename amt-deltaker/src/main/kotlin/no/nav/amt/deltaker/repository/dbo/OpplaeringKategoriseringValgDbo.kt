package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import java.util.UUID

data class OpplaeringKategoriseringValgDbo(
    val representerer: OpplaringKategoriseringResponse.Representerer,
    val kodeverkId: UUID,
    val tekst: String,
)
