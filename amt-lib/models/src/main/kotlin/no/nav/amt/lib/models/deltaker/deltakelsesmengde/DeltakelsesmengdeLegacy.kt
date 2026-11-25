package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.FALLBACK_DELTAKELSESPROSENT

// Bevarer tidligere konverteringsregler for Kafka V1 og arrangørflaten før periodene normaliseres.
fun List<DeltakerHistorikk>.toDeltakelsesmengderMedFallback() = toDeltakelsesmengder { historikk ->
    when (historikk) {
        is DeltakerHistorikk.Endring -> historikk.endring.toDeltakelsesmengde()?.let {
            it.copy(deltakelsesprosent = it.deltakelsesprosent ?: FALLBACK_DELTAKELSESPROSENT)
        }
        is DeltakerHistorikk.Vedtak -> historikk.vedtak.toDeltakelsesmengde()?.takeIf { it.deltakelsesprosent != null }
        is DeltakerHistorikk.ImportertFraArena ->
            historikk.importertFraArena.toDeltakelsesmengde()?.takeIf { it.deltakelsesprosent != null }
        else -> null
    }
}
