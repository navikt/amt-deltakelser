package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanReaktiveres

data class ReaktiverDeltakelseRequest(
    val begrunnelse: String,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: Deltaker) {
        validerDeltakerKanReaktiveres(deltaker)
        validerBegrunnelse(begrunnelse)
    }

    override fun valider(deltaker: DeltakerModel) {
        validerDeltakerKanReaktiveres(deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
