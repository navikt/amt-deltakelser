package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres

data class EndreInnholdRequest(
    val innhold: List<InnholdRequest>,
) : Endringsrequest {
    override fun valider(deltaker: Deltaker) {
        validerDeltakelsesinnhold(innhold, deltaker.deltakerliste.tiltak.innhold, deltaker.deltakerliste.tiltak.tiltakskode)
        validerDeltakerKanEndres(deltaker)
        require(deltakerErEndret(deltaker)) {
            "Innholdet er ikke endret"
        }
    }

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = deltaker.deltakelsesinnhold?.innhold != innhold.toInnholdModel(deltaker)
}
