package no.nav.amt.distribusjon.journalforing.pdf

import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.hendelse.model.deltakerAdresseDeles
import no.nav.amt.distribusjon.hendelse.model.visningsnavn
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.utils.formatDate
import no.nav.amt.distribusjon.utils.formatDateWithMonthName
import no.nav.amt.distribusjon.utils.toTitleCase
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseAnsvarlig
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.hendelse.InnholdDto
import no.nav.amt.lib.models.hendelse.UtkastDto
import no.nav.amt.lib.models.journalforing.pdf.ArrangorDto
import no.nav.amt.lib.models.journalforing.pdf.AvsenderDto
import no.nav.amt.lib.models.journalforing.pdf.EndringDto
import no.nav.amt.lib.models.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.ForslagDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakVedTildeltPlassPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnholdPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnsokingsbrevPdfDto
import no.nav.amt.lib.models.journalforing.pdf.VentelistebrevPdfDto
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate

fun lagHovedvedtakPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    utkast: UtkastDto,
    veileder: HendelseAnsvarlig.NavVeileder,
    vedtaksdato: LocalDate,
    begrunnelseFraNav: String?,
) = HovedvedtakPdfDto(
    deltaker = HovedvedtakPdfDto.DeltakerDto(
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        personident = deltaker.personident,
        innhold = utkast.innhold?.map { it.toInnhold() }?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
        bakgrunnsinformasjon = utkast.bakgrunnsinformasjon,
        deltakelsesmengdeTekst = if (skalViseDeltakelsesmengde(deltaker.deltakerliste.tiltak)) {
            utkast.deltakelsesprosent?.let {
                deltakelsesmengdeTekst(
                    deltakelsesprosent = it.toInt(),
                    dagerPerUke = utkast.dagerPerUke?.toInt(),
                )
            }
        } else {
            null
        },
        adresseDelesMedArrangor = adresseDelesMedArrangor(deltaker, navBruker),
    ),
    deltakerliste = HovedvedtakPdfDto.DeltakerlisteDto(
        navn = deltaker.deltakerliste.tittelVisningsnavn(),
        tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
        ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "", // skal fases ut for innholdV2
        arrangor = HovedvedtakPdfDto.ArrangorDto(
            navn = deltaker.deltakerliste.arrangor.visningsnavn(),
        ),
        forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
        oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
    ),
    avsender = HovedvedtakPdfDto.AvsenderDto(
        navn = veileder.navn,
        enhet = navBruker.navEnhet?.navn ?: "NAV",
    ),
    vedtaksdato = vedtaksdato,
    begrunnelseFraNav = begrunnelseFraNav,
    sidetittel = deltaker.deltakerliste.tittelVisningsnavn(),
    ingressnavn = deltaker.deltakerliste.ingressVisningsnavn(),
    opprettetDato = vedtaksdato,
)

fun lagHovedopptakForTildeltPlass(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    ansvarlig: HendelseAnsvarlig.NavTiltakskoordinator,
    opprettetDato: LocalDate,
    deltakelseInnhold: Deltakelsesinnhold?,
) = HovedvedtakVedTildeltPlassPdfDto(
    deltaker = HovedvedtakVedTildeltPlassPdfDto.DeltakerDto(
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        personident = deltaker.personident,
        innhold = deltakelseInnhold?.innhold?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
    ),
    deltakerliste = HovedvedtakVedTildeltPlassPdfDto.DeltakerlisteDto(
        tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
        ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "", // skal fjernes
        tittelNavn = deltaker.deltakerliste.tittelVisningsnavn(),
        ingressNavn = deltaker.deltakerliste.ingressVisningsnavn(),
        startdato = deltaker.deltakerliste.startdato,
        sluttdato = deltaker.deltakerliste.sluttdato,
        forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
        harKursetStartet = deltaker.deltakerliste.startdato?.isBefore(LocalDate.now()) == true,
        arrangor = ArrangorDto(
            navn = deltaker.deltakerliste.arrangor.visningsnavn(),
        ),
        oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
        harKlagerett = deltaker.deltakerliste.harKlagerett(),
        oppstartstype = Oppstartstype.valueOf(deltaker.deltakerliste.oppstartstype!!.name),
    ),
    avsender = HovedvedtakVedTildeltPlassPdfDto.AvsenderDto(
        navn = ansvarlig.navn,
        enhet = ansvarlig.enhet.navn,
    ),
    opprettetDato = opprettetDato,
)

fun lagInnsokingsbrevPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    veileder: HendelseAnsvarlig.NavVeileder,
    opprettetDato: LocalDate,
    utkast: UtkastDto,
) = InnsokingsbrevPdfDto(
    deltaker = InnsokingsbrevPdfDto.DeltakerDto(
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        personident = deltaker.personident,
        innhold = utkast.innhold?.map { it.toInnhold() }?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
    ),
    deltakerliste = InnsokingsbrevPdfDto.DeltakerlisteDto(
        navn = deltaker.deltakerliste.tittelVisningsnavn(),
        tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
        ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "",
        arrangor = ArrangorDto(
            navn = deltaker.deltakerliste.arrangor.visningsnavn(),
        ),
        startdato = deltaker.deltakerliste.startdato,
        sluttdato = deltaker.deltakerliste.sluttdato,
        oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
        oppstartstype = Oppstartstype.valueOf(deltaker.deltakerliste.oppstartstype!!.name),
    ),
    avsender = AvsenderDto(
        navn = veileder.navn,
        enhet = navBruker.navEnhet?.navn ?: "NAV",
    ),
    sidetittel = deltaker.deltakerliste.tittelVisningsnavn(),
    ingressnavn = deltaker.deltakerliste.ingressVisningsnavn(),
    opprettetDato = opprettetDato,
)

fun lagVentelistebrevPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    endretAv: HendelseAnsvarlig.NavTiltakskoordinator,
    hendelseOpprettetDato: LocalDate,
) = VentelistebrevPdfDto(
    deltaker = VentelistebrevPdfDto.DeltakerDto(
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        personident = deltaker.personident,
        opprettetDato = deltaker.opprettetDato!!,
    ),
    deltakerliste = VentelistebrevPdfDto.DeltakerlisteDto(
        tittelNavn = deltaker.deltakerliste.tittelVisningsnavn(),
        ingressNavn = deltaker.deltakerliste.ingressVisningsnavn(),
        arrangor = ArrangorDto(
            navn = deltaker.deltakerliste.arrangor.visningsnavn(),
        ),
        startdato = deltaker.deltakerliste.startdato,
        sluttdato = deltaker.deltakerliste.sluttdato,
        oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
        oppstartstype = Oppstartstype.valueOf(deltaker.deltakerliste.oppstartstype!!.name),
    ),
    avsender = AvsenderDto(
        navn = endretAv.navn,
        enhet = endretAv.enhet.navn,
    ),
    opprettetDato = hendelseOpprettetDato,
)

fun lagEndringsvedtakPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    ansvarlig: HendelseAnsvarlig,
    hendelser: List<Hendelse>,
    opprettetDato: LocalDate,
): EndringsvedtakPdfDto {
    val endringer = fjernEldreHendelserAvSammeType(hendelser).map { it.payload }

    return EndringsvedtakPdfDto(
        deltaker = EndringsvedtakPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            opprettetDato = deltaker.opprettetDato,
        ),
        deltakerliste = EndringsvedtakPdfDto.DeltakerlisteDto(
            navn = deltaker.deltakerliste.tittelVisningsnavn(),
            ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "",
            arrangor = EndringsvedtakPdfDto.ArrangorDto(
                navn = deltaker.deltakerliste.arrangor.visningsnavn(),
            ),
            forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
            pameldingstype = deltaker.deltakerliste.pameldingstype
                ?: throw IllegalStateException("deltakerliste ${deltaker.deltakerliste.id} må ha påmeldingstype for å lage endringsvedtak"),
            harKlagerett = deltaker.deltakerliste.harKlagerett(),
        ),
        endringer = endringer.map {
            tilEndringDto(
                it,
                deltaker.deltakerliste.tiltak.tiltakskode,
                deltaker.deltakerliste.oppstartstype == HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES ||
                    deltaker.deltakerliste.tiltak.tiltakskode
                        .erOpplaeringstiltak(),
            )
        },
        avsender = EndringsvedtakPdfDto.AvsenderDto(
            navn = ansvarlig.getAvsendernavn(),
            enhet = navBruker.navEnhet?.navn ?: "NAV",
        ),
        vedtaksdato = opprettetDato,
        forsteVedtakFattet = deltaker.forsteVedtakFattet,
        sidetittel = deltaker.deltakerliste.tittelVisningsnavn(),
        ingressnavn = deltaker.deltakerliste.ingressVisningsnavn(),
        opprettetDato = opprettetDato,
    )
}

private fun HendelseAnsvarlig.getAvsendernavn() = when (this) {
    is HendelseAnsvarlig.NavVeileder -> navn

    is HendelseAnsvarlig.NavTiltakskoordinator -> navn

    is HendelseAnsvarlig.Arrangor -> null

    is HendelseAnsvarlig.System,
    is HendelseAnsvarlig.Deltaker,
    -> throw IllegalArgumentException("Kan ikke journalføre endringsvedtak fra deltaker eller system")
}

private fun fjernEldreHendelserAvSammeType(hendelser: List<Hendelse>): List<Hendelse> = hendelser
    .sortedByDescending { it.opprettet }
    .distinctBy { it.payload.javaClass }

private fun skalViseDeltakelsesmengde(tiltakstype: HendelseDeltaker.Deltakerliste.Tiltak): Boolean =
    tiltakstype.tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET ||
        tiltakstype.tiltakskode == Tiltakskode.ARBEIDSFORBEREDENDE_TRENING

fun HendelseDeltaker.Deltakerliste.forskriftskapittel() = when (this.tiltak.tiltakskode) {
    Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> 13

    Tiltakskode.ARBEIDSRETTET_REHABILITERING -> 12

    Tiltakskode.AVKLARING -> 2

    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> 4

    Tiltakskode.JOBBKLUBB -> 4

    Tiltakskode.OPPFOLGING -> 4

    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> 14

    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
    Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    Tiltakskode.STUDIESPESIALISERING,
    Tiltakskode.FAG_OG_YRKESOPPLAERING,
    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
    -> 7

    else -> throw IllegalArgumentException("Ukjent tiltakstype: ${this.tiltak.tiltakskode}")
}

fun HendelseDeltaker.Deltakerliste.tittelVisningsnavn() = when (this.tiltak.tiltakskode) {
    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Varig tilrettelagt arbeid hos ${this.arrangor.visningsnavn()}"

    Tiltakskode.JOBBKLUBB -> "Jobbsøkerkurs hos ${arrangor.visningsnavn()}"

    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring hos ${this.arrangor.visningsnavn()}"

    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring hos ${this.arrangor.visningsnavn()}"

    else -> "${this.tiltak.navn} hos ${arrangor.visningsnavn()}"
}

fun HendelseDeltaker.Deltakerliste.ingressVisningsnavn(): String =
    if (this.tiltak.tiltakskode.erOpplaeringstiltak()) "${this.navn} hos ${arrangor.visningsnavn()}" else tittelVisningsnavn()

fun HendelseDeltaker.Deltakerliste.Arrangor.visningsnavn(): String = with(overordnetArrangor) {
    val visningsnavn = if (this == null || this.navn == "Ukjent Virksomhet") {
        navn
    } else {
        this.navn
    }

    return toTitleCase(visningsnavn)
}

private fun HendelseDeltaker.Deltakerliste.harKlagerett() = !(
    this.tiltak.tiltakskode in setOf(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING, Tiltakskode.ARBEIDSMARKEDSOPPLAERING) &&
        this.oppstartstype == HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES
)

private fun InnholdDto.toInnhold() = Innhold(
    tekst = tekst,
    innholdskode = innholdskode,
    valgt = true,
    beskrivelse = beskrivelse,
)

/*
    Jobbklubb: Ikke fritekst og ikke innholdselementer
    Individuelle tiltak: innholdselementer med annet fritekst
    Opplæringstiltak: fritekst(som er inneholdt i annet checkboks)
 */
fun List<Innhold>.toInnholdPdfDto(ledetekst: String?): InnholdPdfDto? {
    if (this.isEmpty() && ledetekst == null) return null

    val fritekstbeskrivelse = this.firstOrNull { it.innholdskode == "annet" }?.beskrivelse
    val innholdselementer =
        if (this.none { it.innholdskode != "annet" }) {
            emptyList() // hvis det bare er annet innholdselement så skal den ikke vises
        } else {
            this.toVisingstekster()
        }

    return InnholdPdfDto(
        valgteInnholdselementer = innholdselementer,
        fritekstBeskrivelse = fritekstbeskrivelse,
        ledetekst = ledetekst,
    )
}

private fun adresseDelesMedArrangor(deltaker: HendelseDeltaker, navBruker: NavBruker): Boolean =
    navBruker.adressebeskyttelse == null && deltaker.deltakerliste.deltakerAdresseDeles()

private fun List<Innhold>.toVisingstekster() = this.map { innhold ->
    "${innhold.tekst}${innhold.beskrivelse?.let { ": $it" } ?: ""}"
}

private fun tilEndringDto(
    hendelseType: HendelseType,
    tiltakskode: Tiltakskode,
    harFellesAvslutning: Boolean,
): EndringDto = when (hendelseType) {
    is HendelseType.InnbyggerGodkjennUtkast,
    is HendelseType.NavGodkjennUtkast,
    is HendelseType.ReaktiverDeltakelse,
    is HendelseType.EndreSluttarsak,
    is HendelseType.EndreUtkast,
    is HendelseType.OpprettUtkast,
    is HendelseType.AvbrytUtkast,
    is HendelseType.DeltakerSistBesokt,
    is HendelseType.SettPaaVenteliste,
    is HendelseType.TildelPlass,
    -> {
        throw IllegalArgumentException("Skal ikke journalføre $hendelseType som endringsvedtak")
    }

    is HendelseType.AvsluttDeltakelse -> {
        EndringDto.AvsluttDeltakelse(
            aarsak = hendelseType.aarsak?.visningsnavn(),
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Ny sluttdato er ${formatDateWithMonthName(hendelseType.sluttdato)}",
            harDeltatt = true.tilVisningstekst(harFellesAvslutning),
            // Har fullført er alltid true fordi ellers hadde det vært avbrytDeltakelse hendelse
            harFullfort = hendelseType.harFullfort.tilVisningstekst(harFellesAvslutning),
        )
    }

    is HendelseType.EndreAvslutning -> {
        EndringDto.EndreAvslutning(
            aarsak = hendelseType.aarsak?.visningsnavn(),
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Avslutning endret",
            harFullfort = hendelseType.harFullfort.tilVisningstekst(true),
            sluttdato = if (hendelseType.sluttdato != null) "Sluttdato: ${formatDate(hendelseType.sluttdato!!)}" else null,
        )
    }

    is HendelseType.AvbrytDeltakelse -> {
        EndringDto.AvbrytDeltakelse(
            aarsak = hendelseType.aarsak?.visningsnavn(),
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Ny sluttdato er ${formatDateWithMonthName(hendelseType.sluttdato)}",
            harDeltatt = true.tilVisningstekst(harFellesAvslutning),
            // Har fullført er alltid false fordi ellers hadde det vært avsluttdeltakelse hendelse
            harFullfort = false.tilVisningstekst(harFellesAvslutning),
        )
    }

    is HendelseType.EndreDeltakelsesmengde -> {
        EndringDto.EndreDeltakelsesmengde(
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Deltakelsen er endret til ${
                deltakelsesmengdeTekst(
                    deltakelsesprosent = hendelseType.deltakelsesprosent?.toInt(),
                    dagerPerUke = hendelseType.dagerPerUke?.toInt(),
                )
            }",
            gyldigFra = hendelseType.gyldigFra,
        )
    }

    is HendelseType.EndreSluttdato -> {
        EndringDto.EndreSluttdato(
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Ny sluttdato er ${formatDateWithMonthName(hendelseType.sluttdato)}",
        )
    }

    is HendelseType.EndreStartdato -> {
        val tittel =
            hendelseType.startdato?.let { "Oppstartsdato er endret til ${formatDateWithMonthName(it)}" } ?: "Oppstartsdato er fjernet"

        val sluttdato = hendelseType.sluttdato

        if (sluttdato != null) {
            EndringDto.EndreStartdatoOgVarighet(
                sluttdato = "Forventet sluttdato: ${formatDate(sluttdato)}",
                begrunnelseFraNav = hendelseType.begrunnelseFraNav,
                forslagFraArrangor = hendelseType.endringFraForslag?.let {
                    endringFraForslagToForslagDto(
                        it,
                        hendelseType.begrunnelseFraArrangor,
                    )
                },
                tittel = tittel,
            )
        } else {
            EndringDto.EndreStartdato(
                begrunnelseFraNav = hendelseType.begrunnelseFraNav,
                forslagFraArrangor = hendelseType.endringFraForslag?.let {
                    endringFraForslagToForslagDto(
                        it,
                        hendelseType.begrunnelseFraArrangor,
                    )
                },
                tittel = tittel,
            )
        }
    }

    is HendelseType.ForlengDeltakelse -> {
        EndringDto.ForlengDeltakelse(
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
            tittel = "Deltakelsen er forlenget til ${formatDateWithMonthName(hendelseType.sluttdato)}",
        )
    }

    is HendelseType.IkkeAktuell -> {
        EndringDto.IkkeAktuell(
            aarsak = hendelseType.aarsak.visningsnavn(),
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
        )
    }

    is HendelseType.EndreInnhold -> {
        EndringDto.EndreInnhold(
            innhold = hendelseType.innhold.map { it.visningsnavn() },
            innholdBeskrivelse = if (tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET || tiltakskode.erOpplaeringstiltak()) {
                hendelseType.innhold.firstOrNull { it.innholdskode == "annet" }?.beskrivelse
            } else {
                null
            },
        )
    }

    is HendelseType.EndreBakgrunnsinformasjon -> {
        EndringDto.EndreBakgrunnsinformasjon(
            bakgrunnsinformasjon = if (hendelseType.bakgrunnsinformasjon.isNullOrEmpty()) {
                "—"
            } else {
                hendelseType.bakgrunnsinformasjon
            },
        )
    }

    is HendelseType.LeggTilOppstartsdato -> {
        EndringDto.LeggTilOppstartsdato(
            sluttdatoFraArrangor = hendelseType.sluttdato,
            tittel = "Oppstartsdato er ${formatDateWithMonthName(hendelseType.startdato)}",
        )
    }

    is HendelseType.FjernOppstartsdato -> {
        EndringDto.FjernOppstartsdato(
            begrunnelseFraNav = hendelseType.begrunnelseFraNav,
            forslagFraArrangor = hendelseType.endringFraForslag?.let {
                endringFraForslagToForslagDto(
                    it,
                    hendelseType.begrunnelseFraArrangor,
                )
            },
        )
    }

    is HendelseType.Avslag -> {
        EndringDto.Avslag(
            hendelseType.aarsak.visningsnavn(),
            hendelseType.begrunnelseFraNav,
            hendelseType.vurderingFraArrangor?.let {
                EndringDto.Avslag.Vurdering(it.vurderingstype.visningsnavn(), it.begrunnelse)
            },
        )
    }
}

private fun deltakelsesmengdeTekst(deltakelsesprosent: Int?, dagerPerUke: Int?): String {
    val dagerPerUkeTekst = dagerPerUkeTekst(dagerPerUke)?.lowercase()
    if (dagerPerUkeTekst != null) {
        return "${deltakelsesprosent ?: 100} % $dagerPerUkeTekst"
    }
    return "${deltakelsesprosent ?: 100} %"
}

private fun dagerPerUkeTekst(dagerPerUke: Int?): String? {
    if (dagerPerUke != null) {
        return if (dagerPerUke == 1) {
            "fordelt på $dagerPerUke dag i uka"
        } else {
            "fordelt på $dagerPerUke dager i uka"
        }
    }
    return null
}

private fun endringFraForslagToForslagDto(endring: Forslag.Endring, begrunnelseFraArrangor: String?): ForslagDto = when (endring) {
    is Forslag.ForlengDeltakelse -> {
        ForslagDto.ForlengDeltakelse(
            sluttdato = endring.sluttdato,
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.AvsluttDeltakelse -> {
        ForslagDto.AvsluttDeltakelse(
            aarsak = endring.aarsak?.visningsnavn(),
            sluttdato = endring.sluttdato,
            harDeltatt = endring.harDeltatt?.let { if (it) "Ja" else "Nei" },
            harFullfort = endring.harFullfort?.let { if (it) "Ja" else "Nei" },
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.Deltakelsesmengde -> {
        ForslagDto.EndreDeltakelsesmengde(
            deltakelsesmengdeTekst = deltakelsesmengdeTekst(
                deltakelsesprosent = endring.deltakelsesprosent,
                dagerPerUke = endring.dagerPerUke,
            ),
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.IkkeAktuell -> {
        ForslagDto.IkkeAktuell(
            aarsak = endring.aarsak.visningsnavn(),
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.Sluttdato -> {
        ForslagDto.EndreSluttdato(
            sluttdato = endring.sluttdato,
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.Startdato -> {
        if (endring.sluttdato != null) {
            ForslagDto.EndreStartdatoOgVarighet(
                startdato = endring.startdato,
                sluttdato = endring.sluttdato!!,
                begrunnelseFraArrangor = begrunnelseFraArrangor,
            )
        } else {
            ForslagDto.EndreStartdato(
                startdato = endring.startdato,
                begrunnelseFraArrangor = begrunnelseFraArrangor,
            )
        }
    }

    is Forslag.FjernOppstartsdato -> {
        ForslagDto.FjernOppstartsdato(
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.EndreAvslutning -> {
        ForslagDto.EndreAvslutning(
            aarsak = endring.aarsak?.visningsnavn(),
            harDeltatt = endring.harDeltatt?.let { if (it) "Ja" else "Nei" },
            harFullfort = when (endring.harFullfort) {
                true -> endring.harDeltatt?.let { if (it) "Ja" else null }
                false -> endring.harDeltatt?.let { if (it) "Nei" else null }
                null -> null
            },
            begrunnelseFraArrangor = begrunnelseFraArrangor,
        )
    }

    is Forslag.Sluttarsak -> {
        throw IllegalArgumentException("Skal ikke opprette endringsvedtak ved endring av sluttårsak")
    }
}

private fun EndringAarsak.visningsnavn(): String {
    val deltakerEndringAarsak = when (this) {
        is EndringAarsak.FattJobb -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB)
        is EndringAarsak.Annet -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, beskrivelse)
        is EndringAarsak.IkkeMott -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT)
        is EndringAarsak.Syk -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.SYK)
        is EndringAarsak.TrengerAnnenStotte -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.TRENGER_ANNEN_STOTTE)
        is EndringAarsak.Utdanning -> DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.UTDANNING)
    }

    return deltakerEndringAarsak.visningsnavn()
}

private fun EndringFraTiltakskoordinator.Avslag.Aarsak.visningsnavn() = beskrivelse ?: when (this.type) {
    EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT -> "Kurset er fullt"
    EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KRAV_IKKE_OPPFYLT -> "Krav for deltakelse er ikke oppfylt"
    EndringFraTiltakskoordinator.Avslag.Aarsak.Type.ANNET -> "Annet"
}

private fun Vurderingstype.visningsnavn() = when (this) {
    Vurderingstype.OPPFYLLER_KRAVENE -> "Krav for deltakelse er oppfylt"
    Vurderingstype.OPPFYLLER_IKKE_KRAVENE -> "Krav for deltakelse er ikke oppfylt"
}

private fun Boolean?.tilVisningstekst(skalVises: Boolean): String? {
    if (!skalVises) return null
    return when (this) {
        true -> "Ja"
        false -> "Nei"
        else -> null
    }
}
