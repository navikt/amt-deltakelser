package no.nav.amt.lib.models.journalforing.pdf

import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate

data class HovedvedtakVedTildeltPlassPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val avsender: AvsenderDto,
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
        val tiltakskode: Tiltakskode,
        val tittelNavn: String,
        val ingressNavn: String,
        val ledetekst: String?,
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val forskriftskapittel: Int,
        val arrangor: ArrangorDto,
        val oppmoteSted: String?,
        val harKursetStartet: Boolean?,
        val harKlagerett: Boolean,
        val oppstartstype: Oppstartstype
    )

    data class AvsenderDto(
        val navn: String,
        val enhet: String,
    )
}
