package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

data class EndrePrisinfoRequest(
    val prisinformasjon: PrisinformasjonDto,
    val begrunnelse: String?, // påkrevd i frontend, men følger samme mønster som øvrige endringer
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        // merk at begrunnelse påkrevd i frontend, men følger samme mønster som i øvrige klasser
        require(!begrunnelse.isNullOrBlank()) { "Begrunnelse kan ikke være tom" }
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
