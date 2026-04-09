package no.nav.amt.deltaker.enkeltplass.kafka

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface GjennomforingRequestPayload {
    val gjennomforingId: UUID

    data class OpprettEnkeltplass(
        override val gjennomforingId: UUID,
        val tiltakskode: Tiltakskode,
        val prisinformasjon: String,
        val organisasjonsnummer: String,
        val ansvarligEnhet: String, // enhetsnummer
        val opprettetAv: String, // Nav-ident
    ) : GjennomforingRequestPayload
}
