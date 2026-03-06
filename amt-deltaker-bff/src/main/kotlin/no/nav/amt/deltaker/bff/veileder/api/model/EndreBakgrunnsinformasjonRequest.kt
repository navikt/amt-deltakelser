package no.nav.amt.deltaker.bff.veileder.api.model

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres

data class EndreBakgrunnsinformasjonRequest(
    val bakgrunnsinformasjon: String?,
) : Endringsrequest {
    override fun valider(deltaker: Deltaker) {
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakerKanEndres(deltaker)
        require(bakgrunnsinformasjon != deltaker.bakgrunnsinformasjon) {
            "Ingen endring i bakgrunnsinformasjon"
        }
    }
}
