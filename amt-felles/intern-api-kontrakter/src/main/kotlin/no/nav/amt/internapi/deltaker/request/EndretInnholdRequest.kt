package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

data class EndretInnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val innholdselementer: List<InnholdsElementRequest>,
) : EndringRequest {
    override fun toEndring(tiltak: Tiltakstype) = DeltakerEndring.Endring.EndreInnhold(
        ledetekst = tiltak.innhold?.ledetekst,
        innhold = innholdselementer.toInnholdModel(tiltak),
    )
}
