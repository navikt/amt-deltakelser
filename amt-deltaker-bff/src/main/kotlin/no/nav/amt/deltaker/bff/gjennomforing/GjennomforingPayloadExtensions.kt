package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

fun GjennomforingV2KafkaPayload.Gruppe.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
) = Deltakerliste(
    id = id,
    tiltak = tiltakstype,
    navn = navn,
    status = status,
    startDato = startDato,
    sluttDato = sluttDato,
    oppstart = oppstart,
    apentForPamelding = apentForPamelding,
    oppmoteSted = oppmoteSted,
    antallPlasser = antallPlasser,
    pameldingstype = pameldingType,
    arrangor = Deltakerliste.Arrangor(
        arrangor = arrangor,
        overordnetArrangorNavn = null,
    ),
)

fun GjennomforingV2KafkaPayload.Enkeltplass.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
) = Deltakerliste(
    id = id,
    tiltak = tiltakstype,
    navn = tiltakstype.navn,
    status = status,
    startDato = null,
    sluttDato = null,
    oppstart = oppstart,
    apentForPamelding = true,
    oppmoteSted = null,
    antallPlasser = null,
    pameldingstype = pameldingType,
    arrangor = Deltakerliste.Arrangor(
        arrangor = arrangor,
        overordnetArrangorNavn = null,
    ),
)
