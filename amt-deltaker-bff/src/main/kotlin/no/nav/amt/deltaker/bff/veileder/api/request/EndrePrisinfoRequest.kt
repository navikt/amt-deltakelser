package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

data class EndrePrisinfoRequest(
    val prisinformasjon: PrisinformasjonDto,
    val begrunnelse: String,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        // merk at i andre tilsvarende klasser er begrunnelse nullable, men i frontend er
        // begrunnelse påkrevd
        require(begrunnelse.isNotEmpty()) { "Begrunnelse kan ikke være tom" }
        validerBegrunnelse(begrunnelse)

        prisinformasjon.validate().takeIf { it.isNotEmpty() }?.let { valideringsfeil ->
            throw IllegalArgumentException("Prisinformasjon er ikke gyldig: ${valideringsfeil.joinToString()}")
        }

        validerDeltakerKanEndres(
            request = this,
            opprinneligDeltaker = deltaker,
        )
    }
}
