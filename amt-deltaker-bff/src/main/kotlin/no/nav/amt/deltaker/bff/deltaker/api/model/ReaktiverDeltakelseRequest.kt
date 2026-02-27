package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanReaktiveres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker

data class ReaktiverDeltakelseRequest(
    val begrunnelse: String,
) : Endringsrequest {
    override fun valider(deltaker: Deltaker) {
        validerDeltakerKanReaktiveres(deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
