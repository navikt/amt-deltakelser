package no.nav.amt.distribusjon.utils.data

import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.HendelseDto
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.hendelse.InnholdDto
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import no.nav.amt.lib.testing.utils.TestData.randomOrgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

object Hendelsesdata {
    fun lagHendelseDto(
        payload: HendelseType,
        id: UUID = UUID.randomUUID(),
        deltaker: HendelseDeltaker = lagDeltaker(),
        ansvarlig: HendelseAnsvarlig = ansvarligNavVeileder(),
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) = HendelseDto(
        id,
        opprettet,
        deltaker,
        ansvarlig,
        payload,
    )

    fun hendelse(
        payload: HendelseType,
        id: UUID = UUID.randomUUID(),
        deltaker: HendelseDeltaker = lagDeltaker(),
        ansvarlig: HendelseAnsvarlig = ansvarligNavVeileder(),
        opprettet: LocalDateTime = LocalDateTime.now(),
        distribusjonskanal: Distribusjonskanal = Distribusjonskanal.DITT_NAV,
        manuellOppfolging: Boolean = false,
    ) = lagHendelseDto(
        payload,
        id,
        deltaker,
        ansvarlig,
        opprettet,
    ).toModel(distribusjonskanal, manuellOppfolging)

    fun ansvarligNavVeileder(
        id: UUID = UUID.randomUUID(),
        navn: String = "Veilder Veildersen",
        navIdent: String = randomNavIdent(),
        enhet: HendelseAnsvarlig.NavVeileder.Enhet = ansvarligNavEnhet(),
    ) = HendelseAnsvarlig.NavVeileder(id, navn, navIdent, enhet)

    fun ansvarligNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetsnummer: String = randomEnhetsnummer(),
    ) = HendelseAnsvarlig.NavVeileder.Enhet(id, enhetsnummer)

    fun lagDeltaker(
        id: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        deltakerliste: HendelseDeltaker.Deltakerliste = lagDeltakerliste(),
        forsteVedtakFattet: LocalDate? = LocalDate.now().minusDays(3),
        opprettet: LocalDate = LocalDate.now(),
    ) = HendelseDeltaker(
        id = id,
        personident = personident,
        deltakerliste = deltakerliste,
        forsteVedtakFattet = forsteVedtakFattet,
        opprettetDato = opprettet,
        startdato = LocalDate.now().plusDays(1),
        sluttdato = LocalDate.now().plusDays(10),
        status = lagDeltakerStatus(),
    )

    fun lagDeltakerStatus(
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        id: UUID = UUID.randomUUID(),
        aarsakType: DeltakerStatus.Aarsak.Type? = null,
        beskrivelse: String? = null,
        gyldigFra: LocalDateTime = LocalDate.now().atStartOfDay(),
        gyldigTil: LocalDateTime? = null,
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerStatus(
        id,
        statusType,
        aarsakType?.let { DeltakerStatus.Aarsak(it, beskrivelse) },
        gyldigFra,
        gyldigTil,
        opprettet,
    )

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        navn: String = "Deltakerlistenavn",
        arrangor: HendelseDeltaker.Deltakerliste.Arrangor = arrangor(),
        tiltak: HendelseDeltaker.Deltakerliste.Tiltak = tiltak(),
        startdato: LocalDate = LocalDate.now(),
        sluttdato: LocalDate? = LocalDate.now().plusDays(1),
        oppstartstype: Oppstartstype = Oppstartstype.LOPENDE,
        pameldingType: GjennomforingPameldingType = if (oppstartstype ==
            Oppstartstype.LOPENDE
        ) {
            GjennomforingPameldingType.DIREKTE_VEDTAK
        } else {
            GjennomforingPameldingType.TRENGER_GODKJENNING
        },
        opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        prisinformasjon: PrisinformasjonDto? = null,
    ) = HendelseDeltaker.Deltakerliste(
        id = id,
        navn = navn,
        arrangor = arrangor,
        tiltak = tiltak,
        startdato = startdato,
        sluttdato = sluttdato,
        oppstartstype = oppstartstype,
        pameldingstype = pameldingType,
        opplaringKategoriseringValg = opplaringKategoriseringValg,
        prisinformasjon = prisinformasjon,
    )

    fun arrangor(
        id: UUID = UUID.randomUUID(),
        organisasjonsnummer: String = randomOrgnr(),
        navn: String = "Arrangornavn",
        overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null,
    ) = HendelseDeltaker.Deltakerliste.Arrangor(id, organisasjonsnummer, navn, overordnetArrangor)

    fun tiltak(
        navn: String = "Tiltaksnavn",
        tiltakskode: Tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
        ledetekst: String = "Beskrivelse av hva tiltaket går ut på",
    ) = HendelseDeltaker.Deltakerliste.Tiltak(
        navn = navn,
        ledetekst = ledetekst,
        tiltakskode = tiltakskode,
    )
}

object HendelseTypeData {
    fun opprettUtkast(utkast: UtkastDto = utkast()) = HendelseType.OpprettUtkast(utkast)

    fun avbrytUtkast(utkast: UtkastDto = utkast()) = HendelseType.AvbrytUtkast(utkast)

    fun innbyggerGodkjennUtkast(utkast: UtkastDto = utkast()) = HendelseType.InnbyggerGodkjennUtkast(utkast)

    fun navGodkjennUtkast(utkast: UtkastDto = utkast()) = HendelseType.NavGodkjennUtkast(utkast)

    fun enkeltplassEndreOpplaringKategorisering(
        opplaringKategoriseringValg: OpplaringKategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(),
            valgteSertifiseringer = setOf(),
        ),
    ) = HendelseType.EnkeltplassEndreOpplaringKategorisering(opplaringKategoriseringValg)

    fun endreInnhold(innhold: List<InnholdDto> = listOf(innhold())) = HendelseType.EndreInnhold(innhold)

    fun endreDeltakelsesmengde(
        deltakelsesprosent: Float? = 99F,
        dagerPerUke: Float? = 5F,
        gyldigFra: LocalDate = LocalDate.now(),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.Deltakelsesmengde(50, 3, LocalDate.now()),
    ) = HendelseType.EndreDeltakelsesmengde(
        deltakelsesprosent,
        dagerPerUke,
        gyldigFra,
        begrunnelseFraNav,
        begrunnelseFraArrangor,
        endringFraForslag,
    )

    fun endreStartdato(
        startdato: LocalDate? = LocalDate.now().plusDays(7),
        sluttdato: LocalDate? = null,
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.Startdato(LocalDate.now().plusDays(5), null),
    ) = HendelseType.EndreStartdato(startdato, sluttdato, begrunnelseFraNav, begrunnelseFraArrangor, endringFraForslag)

    fun endreSluttdato(
        sluttdato: LocalDate = LocalDate.now().plusDays(7),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.Sluttdato(sluttdato),
    ) = HendelseType.EndreSluttdato(sluttdato, begrunnelseFraNav, begrunnelseFraArrangor, endringFraForslag)

    fun forlengDeltakelse(
        sluttdato: LocalDate = LocalDate.now().plusMonths(2),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.ForlengDeltakelse(sluttdato),
    ) = HendelseType.ForlengDeltakelse(sluttdato, begrunnelseFraNav, begrunnelseFraArrangor, endringFraForslag)

    fun ikkeAktuell(
        aarsak: DeltakerEndring.Aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.IkkeAktuell(EndringAarsak.FattJobb),
    ) = HendelseType.IkkeAktuell(aarsak, begrunnelseFraNav, begrunnelseFraArrangor, endringFraForslag)

    fun avsluttDeltakelse(
        aarsak: DeltakerEndring.Aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        sluttdato: LocalDate = LocalDate.now().plusDays(7),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.AvsluttDeltakelse(sluttdato, EndringAarsak.FattJobb, true, null),
        harFullfort: Boolean? = true,
    ) = HendelseType.AvsluttDeltakelse(
        aarsak = aarsak,
        sluttdato = sluttdato,
        harFullfort = harFullfort,
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag,
    )

    fun endreAvsluttDeltakelse(
        aarsak: DeltakerEndring.Aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        sluttdato: LocalDate = LocalDate.now().plusDays(7),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.AvsluttDeltakelse(sluttdato, EndringAarsak.FattJobb, true, null),
        harFullfort: Boolean? = true,
    ) = HendelseType.EndreAvslutning(
        aarsak = aarsak,
        sluttdato = sluttdato,
        harFullfort = harFullfort,
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag,
    )

    fun endreSluttarsak(
        aarsak: DeltakerEndring.Aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "Noe annet"),
        begrunnelseFraNav: String? = "begrunnelse",
        begrunnelseFraArrangor: String? = "Begrunnelse fra arrangør",
        endringFraForslag: Forslag.Endring? = Forslag.Sluttarsak(EndringAarsak.Annet("annet")),
    ) = HendelseType.EndreSluttarsak(aarsak, begrunnelseFraNav, begrunnelseFraArrangor, endringFraForslag)

    fun sistBesokt(sistBesokt: ZonedDateTime = ZonedDateTime.now()) = HendelseType.DeltakerSistBesokt(sistBesokt)

    fun utkast(
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        dagerPerUke: Float? = 4F,
        deltakelsesprosent: Float = 80F,
        bakgrunnsinformasjon: String = "Bakgrunn for deltakelse på tiltak",
        innhold: List<InnholdDto> = listOf(innhold(), innhold(), innhold()),
    ) = UtkastDto(
        startdato,
        sluttdato,
        dagerPerUke,
        deltakelsesprosent,
        bakgrunnsinformasjon,
        innhold,
    )

    fun innhold() = InnholdDto(
        "Innholdstekst",
        "Innholdskode",
        "Beskrivelse av annet innhold",
    )
}
