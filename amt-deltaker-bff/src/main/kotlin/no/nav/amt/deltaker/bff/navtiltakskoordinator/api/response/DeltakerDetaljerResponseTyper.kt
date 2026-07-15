package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype

data class NavVeilederResponse(
    val navn: String?,
    val telefonnummer: String?,
    val epost: String?,
) {
    constructor(model: no.nav.amt.internapi.deltaker.response.NavVeilederResponse) : this(
        navn = model.navn,
        telefonnummer = model.telefonnummer,
        epost = model.epost,
    )
}

data class VurderingResponse(
    val type: Vurderingstype,
    val begrunnelse: String?,
) {
    constructor(model: no.nav.amt.internapi.deltaker.response.VurderingResponse) : this(
        type = model.type,
        begrunnelse = model.begrunnelse,
    )
}
