package tjenester.nav.valp

import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto

fun GjennomforingFormInput.toKafkaEnkeltplassPayload(): GjennomforingV2KafkaPayload.Enkeltplass {
    return GjennomforingV2KafkaPayload.Enkeltplass(
        id = id,
        opprettetTidspunkt = opprettetTidspunkt,
        oppdatertTidspunkt = oppdatertTidspunkt,
        tiltakskode = tiltakskode,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorOrganisasjonsnummer),
        pameldingType = pameldingType,
        status = status,
        oppstart = oppstart,
        prisinformasjon = prisinformasjon,
    )
}

fun GjennomforingFormInput.toKafkaGruppePayload(): GjennomforingV2KafkaPayload.Gruppe {
    return GjennomforingV2KafkaPayload.Gruppe(
        id = id,
        opprettetTidspunkt = opprettetTidspunkt,
        oppdatertTidspunkt = oppdatertTidspunkt,
        tiltakskode = tiltakskode,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorOrganisasjonsnummer),
        pameldingType = pameldingType,
        status = status,
        oppstart = oppstart,
        navn = requireNotNull(navn) { "Missing navn for gruppe-gjennomforing" },
        startDato = requireNotNull(startDato) { "Missing startDato for gruppe-gjennomforing" },
        sluttDato = sluttDato,
        tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato,
        apentForPamelding = requireNotNull(apentForPamelding) { "Missing apentForPamelding for gruppe-gjennomforing" },
        antallPlasser = requireNotNull(antallPlasser) { "Missing antallPlasser for gruppe-gjennomforing" },
        deltidsprosent = requireNotNull(deltidsprosent) { "Missing deltidsprosent for gruppe-gjennomforing" },
        oppmoteSted = oppmoteSted,
    )
}

fun TiltakstypeFormInput.toKafkaTiltakstypePayload(): TiltakstypeDto {
    return TiltakstypeDto(
        id = id,
        navn = navn,
        tiltakskode = tiltakskode,
        innsatsgrupper = innsatsgrupper,
        deltakerRegistreringInnhold = null,
    )
}

