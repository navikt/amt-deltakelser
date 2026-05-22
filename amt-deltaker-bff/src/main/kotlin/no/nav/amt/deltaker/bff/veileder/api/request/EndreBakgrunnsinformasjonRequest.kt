package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres

data class EndreBakgrunnsinformasjonRequest(
    val bakgrunnsinformasjon: String?,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: Deltaker) {
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakerKanEndres(deltaker)
        require(bakgrunnsinformasjon != deltaker.bakgrunnsinformasjon) {
            "Ingen endring i bakgrunnsinformasjon"
        }
    }

    override fun valider(deltaker: DeltakerModel) {
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakerKanEndres(deltaker)
        require(bakgrunnsinformasjon != deltaker.bakgrunnsinformasjon) {
            "Ingen endring i bakgrunnsinformasjon"
        }
    }
}
