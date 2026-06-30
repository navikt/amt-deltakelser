package no.nav.amt.deltaker.enkeltplass.kafka

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface GjennomforingRequestPayload {
    val gjennomforingId: UUID

    data class EnkeltplassUtkast(
        override val gjennomforingId: UUID,
        val payload: UpsertEnkeltplass,
    ) : GjennomforingRequestPayload

    data class EnkeltplassSoktInn(
        override val gjennomforingId: UUID,
        val payload: UpsertEnkeltplass,
    ) : GjennomforingRequestPayload

    data class UpsertEnkeltplass(
        val tiltakskode: Tiltakskode,
        val organisasjonsnummer: String,
        val prisinformasjon: Prisinformasjon,
        val ansvarligEnhet: String, // enhetsnummer
        val opprettetAv: String, // Nav-ident
        val kategorisering: OpplaringKategorisering?,
    ) {
        data class OpplaringKategorisering(
            val verdier: Map<OpplaringKategoriseringType, Set<UUID>>,
            val sertifiseringer: Set<SertifiseringValg>,
        )
    }

    data class EnkeltplassEndrePrisinformasjon(
        override val gjennomforingId: UUID,
        val payload: Prisinformasjon,
    ) : GjennomforingRequestPayload

    data class EnkeltplassEndreInnhold(
        override val gjennomforingId: UUID,
        val payload: UpsertEnkeltplass.OpplaringKategorisering?,
    ) : GjennomforingRequestPayload

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Prisinformasjon.Anskaffelse::class, name = "Anskaffelse"),
        JsonSubTypes.Type(value = Prisinformasjon.Tilskudd::class, name = "Tilskudd"),
        JsonSubTypes.Type(value = Prisinformasjon.IngenKostnader::class, name = "IngenKostnader"),
    )
    sealed interface Prisinformasjon {
        data class Anskaffelse(
            val pris: Int,
        ) : Prisinformasjon

        data class Tilskudd(
            val tilskudd: Map<Tilskuddstype, Int>,
            val tilleggsopplysninger: String?,
        ) : Prisinformasjon {
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
            enum class Aarsak {
                OPPLAERINGEN_ER_KOSTNADSFRI,
                OPPLAERINGEN_ER_EGENFINANSIERT,
            }
        }

        companion object {
            fun fromAmtPrisinfo(source: PrisinformasjonDto): Prisinformasjon = when (source) {
                is PrisinformasjonDto.Anskaffelse -> Anskaffelse(source.pris)
                is PrisinformasjonDto.Tilskudd -> Tilskudd(
                    tilskudd = source.tilskudd.associate {
                        when (it.type) {
                            PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER -> Tilskudd.Tilskuddstype.SKOLEPENGER
                            PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE -> Tilskudd.Tilskuddstype.STUDIEREISE
                            PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR -> Tilskudd.Tilskuddstype.EKSAMENSGEBYR
                            PrisinformasjonDto.Tilskudd.Tilskuddstype.SEMESTERAVGIFT -> Tilskudd.Tilskuddstype.SEMESTERAVGIFT
                            PrisinformasjonDto.Tilskudd.Tilskuddstype.INTEGRERT_BOTILBUD -> Tilskudd.Tilskuddstype.INTEGRERT_BOTILBUD
                        } to it.pris
                    },
                    tilleggsopplysninger = source.tilleggsopplysninger,
                )

                is PrisinformasjonDto.IngenKostnader -> IngenKostnader(
                    aarsak = when (source.aarsak) {
                        PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI ->
                            IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI

                        PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT ->
                            IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT
                    },
                    tilleggsopplysninger = source.tilleggsopplysninger,
                )
            }
        }
    }
}
