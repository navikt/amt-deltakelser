package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.felles.visningsnavn.kladdTiltakHosArrangorTittel as sharedKladdTiltakHosArrangorTittel
import no.nav.amt.felles.visningsnavn.tiltakHosArrangorIngressTekst as sharedTiltakHosArrangorIngressTekst
import no.nav.amt.felles.visningsnavn.tiltakHosArrangorTittel as sharedTiltakHosArrangorTittel

data class VisningsnavnResponse(
    val tiltakHosArrangorTittel: String,
    val tiltakHosArrangorIngressTekst: String,
    val kladdTiltakHosArrangorTittel: String,
) {
    constructor(gjennomforing: GjennomforingModel) : this(
        tiltakHosArrangorTittel = sharedTiltakHosArrangorTittel(
            tiltakskode = gjennomforing.tiltak.tiltakskode,
            arrangorNavn = gjennomforing.arrangor?.navn,
            opplaringKategoriseringValg = gjennomforing.opplaringKategoriseringValg,
        ),
        tiltakHosArrangorIngressTekst = sharedTiltakHosArrangorIngressTekst(
            tiltakskode = gjennomforing.tiltak.tiltakskode,
            deltakerlisteNavn = gjennomforing.navn,
            arrangorNavn = gjennomforing.arrangor?.navn,
            opplaringKategoriseringValg = gjennomforing.opplaringKategoriseringValg,
        ),
        kladdTiltakHosArrangorTittel = sharedKladdTiltakHosArrangorTittel(
            tiltakskode = gjennomforing.tiltak.tiltakskode,
            deltakerlisteNavn = gjennomforing.navn,
            arrangorNavn = gjennomforing.arrangor?.navn,
            erKladd = gjennomforing.status == GjennomforingStatusType.KLADD,
            opplaringKategoriseringValg = gjennomforing.opplaringKategoriseringValg,
        ),
    )
}
