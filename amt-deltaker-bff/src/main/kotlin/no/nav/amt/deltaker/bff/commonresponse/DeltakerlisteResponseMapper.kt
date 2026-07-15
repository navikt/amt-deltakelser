package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.veileder.api.response.OpplaringKategoriseringValgResponse
import no.nav.amt.deltaker.bff.veileder.api.response.TilgjengeligInnholdResponse

fun GjennomforingModel.toDeltakerlisteResponse() = DeltakerlisteResponse(
    deltakerlisteId = id,
    deltakerlisteNavn = navn,
    tiltakskode = tiltak.tiltakskode,
    arrangorNavn = arrangor?.navn ?: "Ukjent arrangør",
    arrangor = arrangor?.let {
        DeltakerlisteResponse.ArrangorResponse(
            navn = it.navn,
            organisasjonsnummer = it.organisasjonsnummer,
        )
    },
    oppstartstype = oppstart,
    startdato = startDato,
    sluttdato = sluttDato,
    status = status,
    tilgjengeligInnhold = TilgjengeligInnholdResponse.fromDeltakerRegistreringInnhold(
        innhold = tiltak.innhold,
        tiltakstype = tiltak.tiltakskode,
    ),
    erEnkeltplass = erEnkeltplass,
    oppmoteSted = oppmoteSted,
    pameldingstype = pameldingstype ?: no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType.TRENGER_GODKJENNING,
    opplaringKategoriseringValg = OpplaringKategoriseringValgResponse
        .fromOpplaringKategoriseringValg(opplaringKategoriseringValg),
    prisinformasjon = prisinformasjon?.toPrisinformasjonResponse(),
)
