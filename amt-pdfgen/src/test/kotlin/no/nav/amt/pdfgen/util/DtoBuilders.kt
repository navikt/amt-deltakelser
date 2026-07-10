package no.nav.amt.pdfgen.util

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.journalforing.pdf.ArrangorDto
import no.nav.amt.lib.models.journalforing.pdf.AvsenderDto
import no.nav.amt.lib.models.journalforing.pdf.EndringDto
import no.nav.amt.lib.models.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto
import no.nav.amt.lib.models.journalforing.pdf.Forskriftskapittel
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakVedTildeltPlassPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnholdPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnsokingsbrevPdfDto
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
        deltakelsesmengde: String? = "deltakelsesmengde",
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
            forskriftskapittel = Forskriftskapittel.KAPITTEL_14A,
            tiltakskode = tiltakskode,
            oppmoteSted = "Her og der",
        )

    fun hovedvedtakAvsender() = HovedvedtakPdfDto.AvsenderDto(
        navn = "Nav Saksbehandler",
        enhet = "Nav Oslo",
    )

    fun endringsvedtak(
        endringer: List<EndringDto> = listOf(defaultEndring()),
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        klagerett: Boolean = true,
        forskriftskapittel: Forskriftskapittel = Forskriftskapittel.KAPITTEL_14,
    ) = EndringsvedtakPdfDto(
        deltaker = endringsvedtakDeltaker(),
        deltakerliste = endringsvedtakDeltakerliste(
            klagerett = klagerett,
            pameldingstype = pameldingType,
            forskriftskapittel = forskriftskapittel,
        ),
        endringer = endringer,
        avsender = endringsvedtakAvsender(),
        vedtaksdato = fixedDate,
        forsteVedtakFattet = fixedDate.minusDays(10),
        sidetittel = "Endring i tiltak",
        ingressnavn = "Arbeidsforberedende trening",
        opprettetDato = fixedDate.minusMonths(1),
    )

    fun endringsvedtakDeltaker() = EndringsvedtakPdfDto.DeltakerDto(
        fornavn = "Ola",
        mellomnavn = null,
        etternavn = "Nordmann",
        personident = "12345678910",
        opprettetDato = fixedDate,
    )

    fun endringsvedtakDeltakerliste(
        klagerett: Boolean,
        pameldingstype: GjennomforingPameldingType,
        forskriftskapittel: Forskriftskapittel,
    ) = EndringsvedtakPdfDto.DeltakerlisteDto(
        navn = "Tiltaksliste",
        ledetekst = "Dette er ledeteksten",
        arrangor = EndringsvedtakPdfDto.ArrangorDto("Arrangør AS"),
        forskriftskapittel = forskriftskapittel,
        harKlagerett = klagerett,
        pameldingstype = pameldingstype,
    )

    fun defaultEndring() = EndringDto.EndreDeltakelsesmengde(
        tittel = "Deltakelsesmengde er endret",
        begrunnelseFraNav = "Begrunnelse",
        forslagFraArrangor = null,
        gyldigFra = fixedDate,
    )

    fun endringsvedtakAvsender() = EndringsvedtakPdfDto.AvsenderDto(
        navn = "Nav Saksbehandler",
        enhet = "Nav Oslo",
    )

    fun hovedvedtakVedTildeltPlass(
        tiltakskode: Tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        oppstartstype: Oppstartstype = Oppstartstype.FELLES,
        forskriftskapittel: Forskriftskapittel = Forskriftskapittel.KAPITTEL_14A,
        harKursetStartet: Boolean = false,
    ) = HovedvedtakVedTildeltPlassPdfDto(
        deltaker = hovedvedtakVedTildeltPlassDeltaker(),
        deltakerliste = hovedvedtakVedTildeltPlassDeltakerliste(tiltakskode, oppstartstype, forskriftskapittel, harKursetStartet),
        avsender = hovedvedtakVedTildeltPlassAvsender(),
        opprettetDato = fixedDate.minusMonths(1),
    )

    fun hovedvedtakVedTildeltPlassDeltaker() = HovedvedtakVedTildeltPlassPdfDto.DeltakerDto(
        fornavn = "Ola",
        mellomnavn = null,
        etternavn = "Nordmann",
        personident = "12345678910",
        innhold = null,
    )

    fun hovedvedtakVedTildeltPlassDeltakerliste(
        tiltakskode: Tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        oppstartstype: Oppstartstype = Oppstartstype.FELLES,
        forskriftskapittel: Forskriftskapittel = Forskriftskapittel.KAPITTEL_14A,
        harKursetStartet: Boolean = false,
    ) = HovedvedtakVedTildeltPlassPdfDto.DeltakerlisteDto(
        tiltakskode = tiltakskode,
        tittelNavn = "Tiltaksliste",
        ingressNavn = "Arbeidsforberedende trening",
        ledetekst = "Dette er ledeteksten",
        startdato = fixedDate,
        sluttdato = fixedDate.plusMonths(3),
        forskriftskapittel = forskriftskapittel,
        arrangor = ArrangorDto("Arrangør AS"),
        oppmoteSted = "Her og der",
        harKursetStartet = harKursetStartet,
        harKlagerett = true,
        oppstartstype = oppstartstype,
    )

    fun hovedvedtakVedTildeltPlassAvsender() = HovedvedtakVedTildeltPlassPdfDto.AvsenderDto(
        navn = "Nav Saksbehandler",
        enhet = "Nav Oslo",
    )

    fun enkeltplassInnsokingsbrev(
        innhold: EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold,
        tiltaksnavn: String = "Arbeidsforberedende trening",
        arrangor: ArrangorDto = ArrangorDto("Jada Fangst AS"),
        prisinformasjon: EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon,
    ) = EnkeltplassInnsokingsbrevPdfDto(
        deltaker = enkeltplassInnsokingsbrevDeltaker(),
        deltakerliste = enkeltplassInnsokingsbrevDeltakerliste(
            tiltaksnavn = tiltaksnavn,
            arrangor = arrangor,
        ),
        avsender = innsokingsbrevAvsender(),
        opprettetDato = fixedDate.minusMonths(1),
        innhold = innhold,
        innholdFritekst = "Dette er en fritekst",
        deltakelsesmengdeAntallDager = 5,
        prisinformasjon = prisinformasjon,
    )

    fun enkeltplassInnsokingsbrevDeltaker() = EnkeltplassInnsokingsbrevPdfDto.DeltakerDto(
        fornavn = "Ola",
        mellomnavn = "Erik",
        etternavn = "Nordmann",
        personident = "12345678910",
    )

    fun enkeltplassInnsokingsbrevDeltakerliste(
        tiltaksnavn: String = "Arbeidsforberedende trening",
        arrangor: ArrangorDto = ArrangorDto("Jada Fangst AS"),
    ) = EnkeltplassInnsokingsbrevPdfDto.DeltakerlisteDto(
        tiltaksnavn = tiltaksnavn,
        arrangornavn = arrangor.navn,
        startdato = fixedDate,
        sluttdato = fixedDate.plusMonths(3),
        oppstartstype = Oppstartstype.ENKELTPLASS,
    )

    fun innsokingsbrev(
        tiltakskode: Tiltakskode = Tiltakskode.JOBBKLUBB,
        oppstartstype: Oppstartstype = Oppstartstype.FELLES,
        innholdPdfDto: InnholdPdfDto? = null,
    ) = InnsokingsbrevPdfDto(
        deltaker = innsokingsbrevDeltaker(innholdPdfDto),
        deltakerliste = innsokingsbrevDeltakerliste(tiltakskode, oppstartstype),
        avsender = innsokingsbrevAvsender(),
        sidetittel = "Innsøkingsbrev for $tiltakskode",
        ingressnavn = "Jobbklubb",
        opprettetDato = fixedDate.minusMonths(1),
    )

    fun innsokingsbrevDeltaker(innholdPdfDto: InnholdPdfDto? = null) = InnsokingsbrevPdfDto.DeltakerDto(
        fornavn = "Ola",
        mellomnavn = null,
        etternavn = "Nordmann",
        personident = "12345678910",
        innhold = innholdPdfDto,
    )

    fun innsokingsbrevDeltakerliste(
        tiltakskode: Tiltakskode = Tiltakskode.JOBBKLUBB,
        oppstartstype: Oppstartstype = Oppstartstype.FELLES,
    ) = InnsokingsbrevPdfDto.DeltakerlisteDto(
        navn = "Jobbklubb",
        tiltakskode = tiltakskode,
        ledetekst = "Ledetekst for kurset",
        arrangor = ArrangorDto("Arrangør AS"),
        startdato = fixedDate,
        sluttdato = fixedDate.plusMonths(3),
        oppmoteSted = "Møtested AS",
        oppstartstype = oppstartstype,
    )

    fun innsokingsbrevAvsender() = AvsenderDto(
        navn = "Nav Veileder",
        enhet = "Nav Oslo",
    )
}
