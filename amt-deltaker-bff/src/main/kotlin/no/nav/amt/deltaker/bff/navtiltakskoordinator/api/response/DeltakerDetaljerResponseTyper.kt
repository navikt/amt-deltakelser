package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype

data class NavVeilederResponse(
    val navn: String?,
    val telefonnummer: String?,
    val epost: String?,
)

data class VurderingResponse(
    val type: Vurderingstype,
    val begrunnelse: String?,
)

fun no.nav.amt.internapi.deltaker.response.NavVeilederResponse.toNavVeilederResponse() = NavVeilederResponse(
    navn = navn,
    telefonnummer = telefonnummer,
    epost = epost,
)

fun no.nav.amt.internapi.deltaker.response.VurderingResponse.toVurderingResponse() = VurderingResponse(
    type = type,
    begrunnelse = begrunnelse,
)
