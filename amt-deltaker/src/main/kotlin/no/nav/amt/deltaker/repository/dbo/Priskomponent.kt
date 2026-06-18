package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto

data class Priskomponent(
    val type: PrisinformasjonDto.Tilskudd.Tilskuddstype,
    val pris: Int,
)
