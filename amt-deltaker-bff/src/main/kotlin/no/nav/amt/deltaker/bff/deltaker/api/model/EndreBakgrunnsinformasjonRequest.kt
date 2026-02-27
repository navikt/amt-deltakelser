package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker

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
