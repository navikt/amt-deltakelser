package no.nav.amt.lib.models.journalforing.pdf

import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate

data class InnsokingsbrevPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val avsender: AvsenderDto,
    val sidetittel: String,
    val ingressnavn: String,
    val opprettetDato: LocalDate,
) {
    data class DeltakerDto(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val personident: String,
        val innhold: InnholdPdfDto?
        )

    data class DeltakerlisteDto(
        val navn: String,
        val tiltakskode: Tiltakskode,
        val ledetekst: String?,
        val arrangor: ArrangorDto,
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val oppmoteSted: String?,
        val oppstartstype: Oppstartstype,
    )
}
