package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest

data class EndreInnholdRequest(
    val innhold: List<InnholdsElementRequest>,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: Deltaker) {
        validerDeltakelsesinnhold(innhold, deltaker.deltakerliste.tiltak.innhold, deltaker.deltakerliste.tiltak.tiltakskode)
        validerDeltakerKanEndres(deltaker)
        require(deltakerErEndret(deltaker)) {
            "Innholdet er ikke endret"
        }
    }

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = deltaker.deltakelsesinnhold?.innhold != innhold.toInnholdModel(deltaker)
}
