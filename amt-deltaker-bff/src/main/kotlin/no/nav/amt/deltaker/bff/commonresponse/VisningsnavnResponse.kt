package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.felles.visningsnavn.TiltakVisningsnavn
import no.nav.amt.felles.visningsnavn.lagVisningsnavn

data class VisningsnavnResponse(
    val tiltakHosArrangorTittel: String,
    val tiltakHosArrangorIngressTekst: String,
    val kladdTiltakHosArrangorTittel: String,
) {
    companion object {
        fun fraGjennomforing(gjennomforing: GjennomforingModel) = fraVisningsnavn(
            lagVisningsnavn(
                tiltakskode = gjennomforing.tiltak.tiltakskode,
                tiltaksnavn = gjennomforing.tiltak.navn,
                gjennomforingsnavn = gjennomforing.navn,
                gjennomforingType = gjennomforing.type,
                status = gjennomforing.status,
                arrangorNavn = gjennomforing.arrangor?.navn,
                opplaringKategoriseringValg = gjennomforing.opplaringKategoriseringValg,
            ),
        )

        private fun fraVisningsnavn(visningsnavn: TiltakVisningsnavn) = VisningsnavnResponse(
            tiltakHosArrangorTittel = visningsnavn.tittel,
            tiltakHosArrangorIngressTekst = visningsnavn.ingressTekst,
            kladdTiltakHosArrangorTittel = visningsnavn.kladdTittel,
        )
    }
}
