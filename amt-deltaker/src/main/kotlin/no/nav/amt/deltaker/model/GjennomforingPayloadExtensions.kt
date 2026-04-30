package no.nav.amt.deltaker.model

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

fun GjennomforingV2KafkaPayload.Gruppe.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
) = Deltakerliste(
    id = id,
    tiltakstype = tiltakstype,
    navn = navn,
    gjennomforingstype = GjennomforingType.Gruppe,
    status = status,
    startDato = startDato,
    sluttDato = sluttDato,
    antallPlasser = antallPlasser,
    oppstart = oppstart,
    apentForPamelding = apentForPamelding,
    oppmoteSted = oppmoteSted,
    arrangor = arrangor,
    pameldingstype = pameldingType,
    prisinformasjon = null,
)

fun GjennomforingV2KafkaPayload.Enkeltplass.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
) = Deltakerliste(
    id = id,
    tiltakstype = tiltakstype,
    navn = tiltakstype.navn,
    gjennomforingstype = GjennomforingType.Enkeltplass,
    status = status,
    startDato = null,
    sluttDato = null,
    antallPlasser = null,
    oppstart = oppstart,
    apentForPamelding = true,
    oppmoteSted = null,
    arrangor = arrangor,
    pameldingstype = pameldingType,
    prisinformasjon = prisinformasjon,
)
