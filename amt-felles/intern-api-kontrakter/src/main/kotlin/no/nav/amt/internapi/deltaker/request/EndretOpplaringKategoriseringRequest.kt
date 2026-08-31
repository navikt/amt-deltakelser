package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

data class EndretOpplaringKategoriseringRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val beskrivelse: String,
    val opplaringKategoriseringValg: Set<OpplaringKategoriseringValgRequest>,
    val sertifiseringValg: Set<SertifiseringValg>,
    val pavirkerPris: Boolean,
) : EndringRequest {
    fun kodeverkValg(): Set<UUID> = opplaringKategoriseringValg.flatMap { it.valgteIder }.toSet()
}
