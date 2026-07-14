package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype

data class Priskomponent(
    val type: Tilskuddstype,
    val pris: Int,
)
