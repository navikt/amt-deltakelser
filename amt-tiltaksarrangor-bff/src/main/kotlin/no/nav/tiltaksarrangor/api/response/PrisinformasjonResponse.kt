package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.internapi.deltaker.response.TilskuddInfo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

// kopiert fra amt-deltaker-bff
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface PrisinformasjonResponse {
    data class Anskaffelse(
        val pris: Int,
    ) : PrisinformasjonResponse

    data class Tilskudd(
        val tilskudd: List<TilskuddInfo>,
        val tilleggsopplysninger: String?,
    ) : PrisinformasjonResponse

    data class IngenKostnader(
        val tilleggsopplysninger: String,
    ) : PrisinformasjonResponse

    companion object {
        fun fromModel(model: PrisinformasjonDto) = when (model) {
            is PrisinformasjonDto.Anskaffelse -> Anskaffelse(model.pris)
            is PrisinformasjonDto.Tilskudd -> Tilskudd(
                tilskudd = model.tilskudd.map(::TilskuddInfo),
                tilleggsopplysninger = model.tilleggsopplysninger,
            )

            is PrisinformasjonDto.IngenKostnader -> IngenKostnader(
                tilleggsopplysninger = model.tilleggsopplysninger,
            )
        }
    }
}
