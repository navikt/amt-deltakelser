package no.nav.amt.deltaker.enkeltplass.kafka

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
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

    data class EnkeltplassEndrePrisinformasjon(
        override val gjennomforingId: UUID,
        val payload: EnkeltplassPrisinformasjon,
    ) : GjennomforingRequestPayload

    data class EnkeltplassEndreInnhold(
        override val gjennomforingId: UUID,
        val payload: UpsertEnkeltplass.OpplaringKategorisering?,
    ) : GjennomforingRequestPayload

    data class UpsertEnkeltplass(
        val tiltakskode: Tiltakskode,
        val organisasjonsnummer: String,
        val prisinformasjon: String,
        val ansvarligEnhet: String, // enhetsnummer
        val opprettetAv: String, // Nav-ident
        val kategorisering: OpplaringKategorisering?,
    ) {
        data class OpplaringKategorisering(
            val verdier: Map<OpplaringKategoriseringResponse.Representerer, Set<UUID>>,
            val sertifiseringer: Set<SertifiseringValg>,
        )
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface EnkeltplassPrisinformasjon {
        data class Anskaffelse(
            val pris: Int,
        ) : EnkeltplassPrisinformasjon

        data class Tilskudd(
            val tilskudd: Map<Tilskuddstype, Int>,
            val tilleggsopplysninger: String?,
        ) : EnkeltplassPrisinformasjon {
            enum class Tilskuddstype {
                TILTAK_DRIFTSTILSKUDD,
                TILTAK_INVESTERINGER,
                TILTAK_OPPLAERING_TILSKUDD,
            }
        }

        data class IngenKostnader(
            val aarsak: Aarsak,
            val tilleggsopplysninger: String?,
        ) : EnkeltplassPrisinformasjon {
            enum class Aarsak {
                OPPLAERINGEN_ER_KOSTNADSFRI,
                OPPLAERINGEN_ER_EGENFINANSIERT,
            }
        }
    }
}
