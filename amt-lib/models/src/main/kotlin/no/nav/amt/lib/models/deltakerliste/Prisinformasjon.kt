package no.nav.amt.lib.models.deltakerliste

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

const val ANSKAFFELSE_SUB_TYPE = "Anskaffelse"
const val TILSKUDD_SUB_TYPE = "Tilskudd"
const val INGENKOSTNADER_SUB_TYPE = "IngenKostnader"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Prisinformasjon.Anskaffelse::class, name = ANSKAFFELSE_SUB_TYPE),
    JsonSubTypes.Type(value = Prisinformasjon.Tilskudd::class, name = TILSKUDD_SUB_TYPE),
    JsonSubTypes.Type(value = Prisinformasjon.IngenKostnader::class, name = INGENKOSTNADER_SUB_TYPE),
)
sealed interface Prisinformasjon {
    fun sanitize(): Prisinformasjon

    companion object {
        const val MAX_LENGTH_FRITEKST = 600
    }

    data class Anskaffelse(
        val pris: Int,
    ) : Prisinformasjon {
        override fun sanitize(): Prisinformasjon = this
    }

    data class Tilskudd(
        val tilskudd: Map<Tilskuddstype, Int>,
        val tilleggsopplysninger: String?,
    ) : Prisinformasjon {
        override fun sanitize(): Prisinformasjon = copy(
            tilleggsopplysninger = tilleggsopplysninger
                ?.trim()
                ?.take(MAX_LENGTH_FRITEKST),
        )

        enum class Tilskuddstype {
            SKOLEPENGER,
            STUDIEREISE,
            EKSAMENSGEBYR,
            SEMESTERAVGIFT,
            INTEGRERT_BOTILBUD,
        }
    }

    data class IngenKostnader(
        val aarsak: Aarsak,
        val tilleggsopplysninger: String?,
    ) : Prisinformasjon {
        override fun sanitize(): Prisinformasjon = copy(
            tilleggsopplysninger = tilleggsopplysninger
                ?.trim()
                ?.take(MAX_LENGTH_FRITEKST),
        )

        enum class Aarsak {
            OPPLAERINGEN_ER_KOSTNADSFRI,
            OPPLAERINGEN_ER_EGENFINANSIERT,
        }
    }
}
