package no.nav.amt.internapi.enkeltplass

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

const val ANSKAFFELSE_SUB_TYPE = "Anskaffelse"
const val TILSKUDD_SUB_TYPE = "Tilskudd"
const val INGENKOSTNADER_SUB_TYPE = "IngenKostnader"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = PrisinformasjonDto.Anskaffelse::class, name = ANSKAFFELSE_SUB_TYPE),
    JsonSubTypes.Type(value = PrisinformasjonDto.Tilskudd::class, name = TILSKUDD_SUB_TYPE),
    JsonSubTypes.Type(value = PrisinformasjonDto.IngenKostnader::class, name = INGENKOSTNADER_SUB_TYPE),
)
sealed interface PrisinformasjonDto {
    fun validate(): List<String>

    fun sanitize(): PrisinformasjonDto

    companion object {
        const val MAX_LENGTH_TILLEGGSOPPLYSNINGER = 600
        const val POSITIV_PRIS_REQUIRED_MSG = "Pris må være større enn 0"
        const val TILSKUDD_REQUIRED_MSG = "Tilskudd må inneholde minst ett element"
    }

    data class Anskaffelse(
        val pris: Int,
    ) : PrisinformasjonDto {
        override fun validate(): List<String> = if (pris <= 0) listOf(POSITIV_PRIS_REQUIRED_MSG) else emptyList()

        override fun sanitize(): PrisinformasjonDto = this
    }

    data class Tilskudd(
        val tilskudd: List<TilskuddInfo>,
        val tilleggsopplysninger: String?,
    ) : PrisinformasjonDto {
        data class TilskuddInfo(
            val type: Tilskuddstype,
            val pris: Int,
        )

        override fun validate(): List<String> = if (tilskudd.isEmpty()) {
            listOf(TILSKUDD_REQUIRED_MSG)
        } else {
            tilskudd
                .filter { it.pris <= 0 }
                .map { "$POSITIV_PRIS_REQUIRED_MSG. ${it.type}" }
        }

        override fun sanitize(): PrisinformasjonDto = copy(
            tilleggsopplysninger = tilleggsopplysninger
                ?.trim()
                ?.take(MAX_LENGTH_TILLEGGSOPPLYSNINGER),
        )

        enum class Tilskuddstype(
            val sortOrder: Int,
        ) {
            SKOLEPENGER(1),
            SEMESTERAVGIFT(2),
            EKSAMENSGEBYR(3),
            STUDIEREISE(4),
            INTEGRERT_BOTILBUD(5),
        }
    }

    data class IngenKostnader(
        val aarsak: Aarsak,
        val tilleggsopplysninger: String?,
    ) : PrisinformasjonDto {
        override fun validate(): List<String> = emptyList()

        override fun sanitize(): PrisinformasjonDto = copy(
            tilleggsopplysninger = tilleggsopplysninger
                ?.trim()
                ?.take(MAX_LENGTH_TILLEGGSOPPLYSNINGER),
        )

        enum class Aarsak {
            OPPLAERINGEN_ER_KOSTNADSFRI,
            OPPLAERINGEN_ER_EGENFINANSIERT,
        }
    }
}
