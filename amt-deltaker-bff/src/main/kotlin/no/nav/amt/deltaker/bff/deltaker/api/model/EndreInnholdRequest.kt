package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker

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
