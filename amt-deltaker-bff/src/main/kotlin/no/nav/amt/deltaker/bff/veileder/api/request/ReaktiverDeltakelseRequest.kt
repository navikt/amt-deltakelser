package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanReaktiveres

data class ReaktiverDeltakelseRequest(
    val begrunnelse: String,
) : Endringsrequest {
    override fun valider(deltaker: Deltaker) {
        validerDeltakerKanReaktiveres(deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
