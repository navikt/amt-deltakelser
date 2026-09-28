package no.nav.amt.aktivitetskort.kafka.consumer

import no.nav.amt.aktivitetskort.domain.Deltakerliste
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.domain.Tiltakstype
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import java.util.UUID

fun AmtGjennomforingPayload.toTiltakstype() = Tiltakstype(
    id = tiltak.id,
    navn = tiltak.navn,
    tiltakskode = tiltak.tiltakskode,
)

fun AmtGjennomforingPayload.toDeltakerliste(arrangorId: UUID) = Deltakerliste(
    id = id,
    tiltak = Tiltak(tiltak.navn, tiltak.tiltakskode),
    navn = if (type == GjennomforingType.Enkeltplass) tiltak.navn else navn ?: tiltak.navn,
    arrangorId = arrangorId,
)
