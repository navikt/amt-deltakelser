package no.nav.amt.deltaker.enkeltplass.kafka

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
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
            val verdier: Map<OpplaringKategoriseringResponse.Representerer, Set<UUID>>,
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
}
