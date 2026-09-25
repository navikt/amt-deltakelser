package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.model.Tiltak
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload.TiltakPayload

/**
 * Bygger den slanke [TiltakPayload]-modellen fra den embeddede tiltak-blokken i [AmtGjennomforingPayload].
 * bff sin persisterte deltakerliste bruker kun id/navn/tiltakskode.
 */
fun AmtGjennomforingPayload.toTiltak() = Tiltak(
    id = tiltak.id,
    navn = tiltak.navn,
    tiltakskode = tiltak.tiltakskode,
)

/**
 * Bygger bff sin [Deltakerliste]-modell fra [AmtGjennomforingPayload]. Kun feltene bff faktisk
 * bruker i tiltakskoordinator-tilgangsflyten. Tiltakstype-FK-en settes separat i repository-laget.
 */
fun AmtGjennomforingPayload.toDeltakerliste(arrangor: Arrangor) = Deltakerliste(
    id = id,
    status = status,
    sluttDato = sluttDato,
    oppstart = oppstart,
    pameldingstype = pameldingstype,
    arrangor = arrangor,
)
