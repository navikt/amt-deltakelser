package no.nav.amt.deltaker.bff.commonresponse

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.internapi.deltaker.response.TilskuddInfo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface PrisinformasjonResponse {
    data class Anskaffelse(
        val pris: Int,
        val begrunnelse: String?,
    ) : PrisinformasjonResponse

    data class Tilskudd(
        val tilskudd: List<TilskuddInfo>,
        val tilleggsopplysninger: String?,
        val begrunnelse: String?,
    ) : PrisinformasjonResponse

    data class IngenKostnader(
        val tilleggsopplysninger: String,
        val begrunnelse: String,
    ) : PrisinformasjonResponse

    companion object {
        fun fromModel(model: PrisinformasjonDto) = when (model) {
            is PrisinformasjonDto.Anskaffelse -> Anskaffelse(
                pris = model.pris,
                begrunnelse = model.begrunnelse,
            )

            is PrisinformasjonDto.Tilskudd -> Tilskudd(
                tilskudd = model.tilskudd.map(::TilskuddInfo),
                tilleggsopplysninger = model.tilleggsopplysninger,
                begrunnelse = model.begrunnelse,
            )

            is PrisinformasjonDto.IngenKostnader -> IngenKostnader(
                tilleggsopplysninger = model.tilleggsopplysninger,
                begrunnelse = model.begrunnelse,
            )
        }
    }
}
