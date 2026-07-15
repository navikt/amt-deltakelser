package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto

sealed interface PrisinformasjonResponse {
    data class Anskaffelse(
        val pris: Int,
    ) : PrisinformasjonResponse

    data class Tilskudd(
        val tilskudd: List<TilskuddInfo>,
        val tilleggsopplysninger: String?,
    ) : PrisinformasjonResponse {
        data class TilskuddInfo(
            val type: PrisinformasjonDto.Tilskudd.Tilskuddstype,
            val pris: Int,
        )
    }

    data class IngenKostnader(
        val aarsak: PrisinformasjonDto.IngenKostnader.Aarsak,
        val tilleggsopplysninger: String?,
    ) : PrisinformasjonResponse
}

fun PrisinformasjonDto.toPrisinformasjonResponse(): PrisinformasjonResponse = when (this) {
    is PrisinformasjonDto.Anskaffelse -> PrisinformasjonResponse.Anskaffelse(pris)
    is PrisinformasjonDto.Tilskudd -> PrisinformasjonResponse.Tilskudd(
        tilskudd = tilskudd.map {
            PrisinformasjonResponse.Tilskudd.TilskuddInfo(
                type = it.type,
                pris = it.pris,
            )
        },
        tilleggsopplysninger = tilleggsopplysninger,
    )

    is PrisinformasjonDto.IngenKostnader -> PrisinformasjonResponse.IngenKostnader(
        aarsak = aarsak,
        tilleggsopplysninger = tilleggsopplysninger,
    )
}
