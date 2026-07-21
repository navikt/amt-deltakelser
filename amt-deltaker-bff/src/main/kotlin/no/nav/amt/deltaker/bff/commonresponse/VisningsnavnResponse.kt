package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.felles.visningsnavn.lagVisningsnavn
import no.nav.amt.deltaker.bff.model.GjennomforingModel

data class VisningsnavnResponse(
    val tiltakHosArrangorTittel: String,
    val tiltakHosArrangorIngressTekst: String,
    val kladdTiltakHosArrangorTittel: String,
) {
    constructor(gjennomforing: GjennomforingModel) : this(
        lagVisningsnavn(
            type = gjennomforing.type,
            tiltakskode = gjennomforing.tiltak.tiltakskode,
            tiltaksnavn = gjennomforing.tiltak.navn,
            gjennomforingsnavn = gjennomforing.navn,
            status = gjennomforing.status,
            arrangorNavn = gjennomforing.arrangor?.navn,
            opplaringKategoriseringValg = gjennomforing.opplaringKategoriseringValg,
        ),
    )

    private constructor(visningsnavn: no.nav.amt.felles.visningsnavn.Visningsnavn) : this(
        tiltakHosArrangorTittel = visningsnavn.tiltakHosArrangorTittel,
        tiltakHosArrangorIngressTekst = visningsnavn.tiltakHosArrangorIngressTekst,
        kladdTiltakHosArrangorTittel = visningsnavn.kladdTiltakHosArrangorTittel,
    )
}
