package no.nav.amt.lib.models.journalforing.pdf

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate

data class HovedvedtakPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val avsender: AvsenderDto,
    val vedtaksdato: LocalDate,
    val begrunnelseFraNav: String? = null,
    val sidetittel: String,
    val ingressnavn: String,
    val opprettetDato: LocalDate,
) {
    data class DeltakerDto(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val personident: String,
        val innhold: InnholdPdfDto?,
        val bakgrunnsinformasjon: String?,
        val deltakelsesmengdeTekst: String?,
        val adresseDelesMedArrangor: Boolean,
    )

    data class DeltakerlisteDto(
        val navn: String,
        val tiltakskode: Tiltakskode,
        val ledetekst: String,
        val arrangor: ArrangorDto,
        val forskriftskapittel: Int,
        val oppmoteSted: String?,
    )

    data class ArrangorDto(
        val navn: String,
    )

    data class AvsenderDto(
        val navn: String,
        val enhet: String,
    )
}
