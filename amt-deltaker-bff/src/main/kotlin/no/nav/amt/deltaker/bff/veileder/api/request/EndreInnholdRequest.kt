package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.request.toInnholdModel

data class EndreInnholdRequest(
    val innhold: List<InnholdsElementRequest>,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        validerDeltakelsesinnhold(innhold, deltaker.gjennomforing.tiltak.innhold, deltaker.gjennomforing.tiltak.tiltakskode)
        validerDeltakerKanEndres(this, deltaker)
        require(deltakerErEndret(deltaker)) {
            "Innholdet er ikke endret"
        }
    }

    private fun deltakerErEndret(deltaker: DeltakerModel): Boolean =
        deltaker.deltakelsesinnhold?.innhold != innhold.toInnholdModel(deltaker.gjennomforing.tiltak)
}
