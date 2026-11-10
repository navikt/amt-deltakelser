package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

data class TilskuddInfo(
    val type: PrisinformasjonDto.Tilskudd.Tilskuddstype,
    val pris: Int,
) {
    constructor(model: PrisinformasjonDto.Tilskudd.TilskuddInfo) : this(
        type = model.type,
        pris = model.pris,
    )
}
