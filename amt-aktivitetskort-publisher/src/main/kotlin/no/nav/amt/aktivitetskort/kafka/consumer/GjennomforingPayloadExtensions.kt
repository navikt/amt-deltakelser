package no.nav.amt.aktivitetskort.kafka.consumer

import no.nav.amt.aktivitetskort.domain.Deltakerliste
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import java.util.UUID

fun GjennomforingV2KafkaPayload.Gruppe.toModel(
    arrangorId: UUID,
    navnTiltakstype: String,
) = Deltakerliste(
    id = this.id,
    tiltak = Tiltak(navnTiltakstype, tiltakskode),
    navn = navn,
    arrangorId = arrangorId,
)

fun GjennomforingV2KafkaPayload.Enkeltplass.toModel(
    arrangorId: UUID,
    navnTiltakstype: String,
) = Deltakerliste(
    id = this.id,
    tiltak = Tiltak(navnTiltakstype, tiltakskode),
    navn = navnTiltakstype,
    arrangorId = arrangorId,
)
