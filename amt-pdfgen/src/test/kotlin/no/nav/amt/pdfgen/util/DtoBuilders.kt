package no.nav.amt.pdfgen.util

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.journalforing.pdf.EndringDto
import no.nav.amt.lib.models.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnholdPdfDto
import no.nav.amt.pdfgen.util.RenderUtils.fixedDate

object DtoBuilders {
    fun hovedvedtak(
        tiltakskode: Tiltakskode,
        innholdPdfDto: InnholdPdfDto? = null,
        deltaker: HovedvedtakPdfDto.DeltakerDto = hovedvedtakDeltaker(innholdPdfDto),
    ) = HovedvedtakPdfDto(
        deltaker = deltaker,
        deltakerliste = hovedvedtakDeltakerliste(tiltakskode),
        avsender = hovedvedtakAvsender(),
        vedtaksdato = fixedDate,
        sidetittel = "sidetittel: " + tiltakskode.name,
        ingressnavn = "Arbeidsforberedende trening",
        opprettetDato = fixedDate.minusMonths(1),
    )

    fun hovedvedtakDeltaker(
        innholdPdfDto: InnholdPdfDto? = null,
        bakgrunnsinfo: String = "Bakgrunnsinfo",
        deltakelsesmengde: String = "deltakelsesmengde",
    ) = HovedvedtakPdfDto.DeltakerDto(
        fornavn = "Ola",
        mellomnavn = null,
        etternavn = "Nordmann",
        personident = "12345678910",
        innhold = innholdPdfDto,
        bakgrunnsinformasjon = bakgrunnsinfo,
        deltakelsesmengdeTekst = deltakelsesmengde,
        adresseDelesMedArrangor = false,
    )

    fun hovedvedtakDeltakerliste(tiltakskode: Tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING) =
        HovedvedtakPdfDto.DeltakerlisteDto(
            navn = "Tiltaksliste",
            ledetekst = "Dette er ledeteksten",
            arrangor = HovedvedtakPdfDto.ArrangorDto("Arrangør AS"),
            forskriftskapittel = 42,
            tiltakskode = tiltakskode,
            oppmoteSted = "Her og der",
        )

    fun hovedvedtakAvsender() =
        HovedvedtakPdfDto.AvsenderDto(
            navn = "Nav Saksbehandler",
            enhet = "Nav Oslo",
        )

    fun endringsvedtak(
        endringer: List<EndringDto> = listOf(defaultEndring()),
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        klagerett: Boolean = true,
    ) = EndringsvedtakPdfDto(
        deltaker = endringsvedtakDeltaker(),
        deltakerliste = endringsvedtakDeltakerliste(klagerett, pameldingType),
        endringer = endringer,
        avsender = endringsvedtakAvsender(),
        vedtaksdato = fixedDate,
        forsteVedtakFattet = fixedDate.minusDays(10),
        sidetittel = "Endring i tiltak",
        ingressnavn = "Arbeidsforberedende trening",
        opprettetDato = fixedDate.minusMonths(1),
    )

    fun endringsvedtakDeltaker() =
        EndringsvedtakPdfDto.DeltakerDto(
            fornavn = "Ola",
            mellomnavn = null,
            etternavn = "Nordmann",
            personident = "12345678910",
            opprettetDato = fixedDate,
        )

    fun endringsvedtakDeltakerliste(
        klagerett: Boolean,
        pameldingstype: GjennomforingPameldingType,
    ) = EndringsvedtakPdfDto.DeltakerlisteDto(
        navn = "Tiltaksliste",
        ledetekst = "Dette er ledeteksten",
        arrangor = EndringsvedtakPdfDto.ArrangorDto("Arrangør AS"),
        forskriftskapittel = 42,
        harKlagerett = klagerett,
        pameldingstype = pameldingstype,
    )

    fun defaultEndring() =
        EndringDto.EndreDeltakelsesmengde(
            tittel = "Deltakelsesmengde er endret",
            begrunnelseFraNav = "Begrunnelse",
            forslagFraArrangor = null,
            gyldigFra = fixedDate,
        )

    fun endringsvedtakAvsender() =
        EndringsvedtakPdfDto.AvsenderDto(
            navn = "Nav Saksbehandler",
            enhet = "Nav Oslo",
        )
}
