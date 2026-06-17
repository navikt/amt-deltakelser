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
    fun validate(): List<String>

    fun sanitize(): Prisinformasjon

    companion object {
        const val MAX_LENGTH_TILLEGGSOPPLYSNINGER = 600
        const val POSITIV_PRIS_REQUIRED_MSG = "Pris må være større enn 0"
        const val TILSKUDD_REQUIRED_MSG = "Tilskudd må inneholde minst ett element"
    }

    data class Anskaffelse(
        val pris: Int,
    ) : Prisinformasjon {
        override fun validate(): List<String> = if (pris <= 0) listOf(POSITIV_PRIS_REQUIRED_MSG) else emptyList()

        override fun sanitize(): Prisinformasjon = this
    }

    data class Tilskudd(
        val tilskudd: Map<Tilskuddstype, Int>,
        val tilleggsopplysninger: String?,
    ) : Prisinformasjon {
        override fun validate(): List<String> = if (tilskudd.isEmpty()) {
            listOf(TILSKUDD_REQUIRED_MSG)
        } else {
            tilskudd.entries
                .filter { it.value <= 0 }
                .map { "$POSITIV_PRIS_REQUIRED_MSG. ${it.key.name}" }
        }

        override fun sanitize(): Prisinformasjon = copy(
            tilleggsopplysninger = tilleggsopplysninger
                ?.trim()
                ?.take(MAX_LENGTH_TILLEGGSOPPLYSNINGER),
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
        override fun validate(): List<String> = emptyList()

        override fun sanitize(): Prisinformasjon = copy(
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
