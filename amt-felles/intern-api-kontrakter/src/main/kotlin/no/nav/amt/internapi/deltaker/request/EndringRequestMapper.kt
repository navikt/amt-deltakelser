package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvbrytDeltakelse
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvsluttDeltakelse
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreAvslutning
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreBakgrunnsinformasjon
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreInnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreOpplaringKategorisering
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndrePrisinfo
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttarsak
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttdato
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreStartdato
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.FjernOppstartsdato
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ForlengDeltakelse
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.IkkeAktuell
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ReaktiverDeltakelse
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate

object EndringRequestMapper {
    /**
     * Konverterer en [EndringRequest] til en [DeltakerEndring.Endring].
     *
     * Kontekstuell data som ikke er tilgjengelig i requesten må sendes inn eksplisitt:
     * - [tiltakstype] kreves for [EndretInnholdRequest] (brukes til å hente ledetekst og mappe innholdselementer)
     * - [opplaringKategoriseringValg] kreves for [EndretOpplaringKategoriseringRequest]
     *
     * @param request requesten som skal konverteres
     * @param tiltakstype tiltakstypen til deltakerens gjennomføring
     * @param opplaringKategoriseringValg gjeldende kategorisering for gjennomføringen
     * @return domeneobjektet som representerer endringen
     * @throws IllegalArgumentException hvis påkrevd kontekst mangler for den aktuelle request-typen
     */
    fun toEndring(
        request: EndringRequest,
        tiltakstype: Tiltakstype? = null,
        opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
    ): DeltakerEndring.Endring = when (request) {
        is AvbrytDeltakelseRequest -> AvbrytDeltakelse(
            aarsak = request.aarsak,
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
        )

        is AvsluttDeltakelseRequest -> AvsluttDeltakelse(
            aarsak = request.aarsak,
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
            harFullfort = request.harFullfort,
        )

        is BakgrunnsinformasjonRequest -> EndreBakgrunnsinformasjon(request.bakgrunnsinformasjon)

        is DeltakelsesmengdeRequest -> EndreDeltakelsesmengde(
            deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
            dagerPerUke = request.dagerPerUke?.toFloat(),
            begrunnelse = request.begrunnelse,
            gyldigFra = request.gyldigFra,
            pavirkerPris = request.pavirkerPris,
        )

        is EndreAvslutningRequest -> EndreAvslutning(
            aarsak = request.aarsak,
            harFullfort = request.harFullfort,
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
        )

        is EndretInnholdRequest -> {
            val valgtTiltakstype = requireNotNull(tiltakstype) {
                "${request::class.simpleName} krever tiltakstype for å mappe innhold"
            }

            EndreInnhold(
                ledetekst = valgtTiltakstype.innhold?.ledetekst,
                innhold = request.innholdselementer.toInnholdModel(valgtTiltakstype),
            )
        }

        is EndretOpplaringKategoriseringRequest -> EndreOpplaringKategorisering(
            opplaringKategoriseringValg = requireNotNull(opplaringKategoriseringValg) {
                "${request::class.simpleName} krever opplaringKategoriseringValg"
            },
            beskrivelse = request.beskrivelse,
        )

        is EndretPrisinfoRequest -> EndrePrisinfo(
            prisinfo = request.prisinfo,
            begrunnelse = request.begrunnelse,
            prisinformasjonId = request.prisinformasjonId,
        )

        is FjernOppstartsdatoRequest -> FjernOppstartsdato(
            begrunnelse = request.begrunnelse,
        )

        is ForlengDeltakelseRequest -> ForlengDeltakelse(
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
            pavirkerPris = request.pavirkerPris,
        )

        is IkkeAktuellRequest -> IkkeAktuell(
            aarsak = request.aarsak,
            begrunnelse = request.begrunnelse,
        )

        is ReaktiverDeltakelseRequest -> ReaktiverDeltakelse(
            reaktivertDato = LocalDate.now(),
            begrunnelse = request.begrunnelse,
        )

        is SluttarsakRequest -> EndreSluttarsak(
            aarsak = request.aarsak,
            begrunnelse = request.begrunnelse,
        )

        is SluttdatoRequest -> EndreSluttdato(
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
        )

        is StartdatoRequest -> EndreStartdato(
            startdato = request.startdato,
            sluttdato = request.sluttdato,
            begrunnelse = request.begrunnelse,
            pavirkerPris = request.pavirkerPris,
        )
    }
}
