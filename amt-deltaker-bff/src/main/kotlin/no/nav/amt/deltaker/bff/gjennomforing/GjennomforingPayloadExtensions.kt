package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.kafka.GjennomforingV2KafkaPayload

fun GjennomforingV2KafkaPayload.Gruppe.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
): Deltakerliste {
    // Dette er en midlertidig løsning inntil Valp sender med dette feltet
    val datoAvsluttendeStatus = when (status) {
        GjennomforingStatusType.AVSLUTTET,
        GjennomforingStatusType.AVBRUTT,
        -> sluttDato

        // Her har vi ingen dato, benytter derfor startDato fordi den kommer først i tid
        GjennomforingStatusType.AVLYST -> startDato
        else -> null
    }

    return Deltakerliste(
        id = id,
        tiltak = tiltakstype,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        datoAvsluttendeStatus = datoAvsluttendeStatus,
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
}

fun GjennomforingV2KafkaPayload.Enkeltplass.toModel(
    arrangor: Arrangor,
    tiltakstype: Tiltakstype,
) = Deltakerliste(
    id = id,
    tiltak = tiltakstype,
    navn = tiltakstype.navn,
    status = status,
    oppstart = oppstart,
    apentForPamelding = true,
    pameldingstype = pameldingType,
    arrangor = Deltakerliste.Arrangor(
        arrangor = arrangor,
        overordnetArrangorNavn = null,
    ),
)
