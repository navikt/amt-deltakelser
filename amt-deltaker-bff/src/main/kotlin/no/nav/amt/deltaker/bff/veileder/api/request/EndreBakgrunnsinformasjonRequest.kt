package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres

data class EndreBakgrunnsinformasjonRequest(
    val bakgrunnsinformasjon: String?,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakerKanEndres(this, deltaker)
        require(bakgrunnsinformasjon != deltaker.bakgrunnsinformasjon) {
            "Ingen endring i bakgrunnsinformasjon"
        }
    }
}
