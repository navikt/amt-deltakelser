package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.veileder.api.response.OpplaringKategoriseringValgResponse
import no.nav.amt.deltaker.bff.veileder.api.response.TilgjengeligInnholdResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.TiltakskodeDto
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteResponse(
    val deltakerlisteId: UUID,
    val deltakerlisteNavn: String,
    val tiltakskode: Tiltakskode,
    val arrangorNavn: String, // skal fjernes
    val arrangor: ArrangorResponse?,
    val oppstartstype: Oppstartstype?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val status: GjennomforingStatusType?,
    val tilgjengeligInnhold: TilgjengeligInnholdResponse?,
    val erEnkeltplass: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
    val opplaringKategoriseringValg: OpplaringKategoriseringValgResponse? = null,
    val prisinformasjon: PrisinformasjonResponse? = null,
) {

    val tiltakskodeDto: TiltakskodeDto = TiltakskodeDto(tiltakskode)

    constructor(model: GjennomforingModel) : this(
        deltakerlisteId = model.id,
        deltakerlisteNavn = model.navn,
        tiltakskode = model.tiltak.tiltakskode,
        arrangorNavn = model.arrangor?.navn ?: "Ukjent arrangør",
        arrangor = model.arrangor?.let(::ArrangorResponse),
        oppstartstype = model.oppstart,
        startdato = model.startDato,
        sluttdato = model.sluttDato,
        status = model.status,
        tilgjengeligInnhold = TilgjengeligInnholdResponse(model.tiltak.innhold, model.tiltak.tiltakskode),
        erEnkeltplass = model.erEnkeltplass,
        oppmoteSted = model.oppmoteSted,
        pameldingstype = model.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
        opplaringKategoriseringValg = model.opplaringKategoriseringValg?.let(::OpplaringKategoriseringValgResponse),
        prisinformasjon = model.prisinformasjon?.let(PrisinformasjonResponse::fromModel),
    )
}

data class ArrangorResponse(
    val navn: String,
    val organisasjonsnummer: String,
) {
    constructor(model: ArrangorModel) : this(
        navn = model.navn,
        organisasjonsnummer = model.organisasjonsnummer,
    )
}
