package no.nav.amt.distribusjon.journalforing.pdf

import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.hendelse.model.deltakerAdresseDelesMedArrangor
import no.nav.amt.distribusjon.hendelse.model.visningsnavn
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.utils.formatDate
import no.nav.amt.distribusjon.utils.formatDateWithMonthName
import no.nav.amt.felles.visningsnavn.TiltakVisningsnavn
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.hendelse.InnholdDto
import no.nav.amt.internapi.journalforing.pdf.EndringDto
import no.nav.amt.internapi.journalforing.pdf.Forskriftskapittel
import no.nav.amt.internapi.journalforing.pdf.ForslagDto
import no.nav.amt.internapi.journalforing.pdf.InnholdPdfDto
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innhold.Companion.INNHOLDSKODE_ANNET
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.toTitleCase

fun HendelseAnsvarlig.getAvsendernavn() = when (this) {
    is HendelseAnsvarlig.NavVeileder -> navn

    is HendelseAnsvarlig.NavTiltakskoordinator -> navn

    is HendelseAnsvarlig.Arrangor -> null

    is HendelseAnsvarlig.System,
    is HendelseAnsvarlig.Deltaker,
    -> throw IllegalArgumentException("Kan ikke journalføre endringsvedtak fra deltaker eller system")
}

fun fjernEldreHendelserAvSammeType(hendelser: List<Hendelse>): List<Hendelse> = hendelser
    .sortedByDescending { it.opprettet }
    .distinctBy { it.payload.javaClass }

fun HendelseDeltaker.Deltakerliste.forskriftskapittel(): Forskriftskapittel = when (this.tiltak.tiltakskode) {
    Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> Forskriftskapittel.KAPITTEL_13

    Tiltakskode.ARBEIDSRETTET_REHABILITERING -> Forskriftskapittel.KAPITTEL_12

    Tiltakskode.AVKLARING -> Forskriftskapittel.KAPITTEL_2

    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
    Tiltakskode.JOBBKLUBB,
    Tiltakskode.OPPFOLGING,
    -> Forskriftskapittel.KAPITTEL_4

    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> Forskriftskapittel.KAPITTEL_14
    Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER -> Forskriftskapittel.KAPITTEL_14A

    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
    Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    Tiltakskode.STUDIESPESIALISERING,
    Tiltakskode.FAG_OG_YRKESOPPLAERING,
    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
    -> Forskriftskapittel.KAPITTEL_7

    else -> throw IllegalArgumentException("Ukjent tiltakstype: ${this.tiltak.tiltakskode}")
}

fun HendelseDeltaker.Deltakerliste.visningsnavn() = TiltakVisningsnavn.lagVisningsnavn(
    tiltakskode = tiltak.tiltakskode,
    tiltaksnavn = tiltak.navn,
    gjennomforingsnavn = navn,
    gjennomforingType = if (erEnkeltplass == true) GjennomforingType.Enkeltplass else GjennomforingType.Gruppe,
    erKladd = false,
    arrangorNavn = arrangor.visningsnavn(),
    opplaringKategoriseringValg = opplaringKategoriseringValg,
)

fun HendelseDeltaker.Deltakerliste.Arrangor.visningsnavn(): String = with(overordnetArrangor) {
    val visningsnavn = if (this == null || this.navn == "Ukjent Virksomhet") {
        navn
    } else {
        this.navn
    }

    return visningsnavn.toTitleCase()
}

fun HendelseDeltaker.Deltakerliste.harKlagerett() = !(
    this.tiltak.tiltakskode in setOf(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING, Tiltakskode.ARBEIDSMARKEDSOPPLAERING) &&
        this.oppstartstype == Oppstartstype.FELLES
)

fun InnholdDto.toInnhold() = Innhold(
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

    return if (this.none { it.innholdskode != INNHOLDSKODE_ANNET }) {
        InnholdPdfDto(
            valgteInnholdselementer = emptyList(),
            fritekstBeskrivelse = this.firstOrNull { it.innholdskode == INNHOLDSKODE_ANNET }?.beskrivelse,
            ledetekst = ledetekst,
        )
    } else {
        InnholdPdfDto(
            valgteInnholdselementer = this.toVisingstekster(),
            fritekstBeskrivelse = null,
            ledetekst = ledetekst,
        )
    }
}

fun adresseDelesMedArrangor(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
): Boolean = navBruker.adressebeskyttelse == null && deltaker.deltakerliste.deltakerAdresseDelesMedArrangor()

private fun List<Innhold>.toVisingstekster() = this.map { innhold ->
    "${innhold.tekst}${innhold.beskrivelse?.let { ": $it" } ?: ""}"
}

fun tilEndringDto(
    hendelseType: HendelseType,
    tiltakskode: Tiltakskode,
    erEnkeltplass: Boolean?,
    harFellesAvslutning: Boolean,
): EndringDto = when (hendelseType) {
    is HendelseType.InnbyggerGodkjennUtkast,
    is HendelseType.NavGodkjennUtkast,
    is HendelseType.EnkeltplassOkonomiGodkjennUtkast,
    is HendelseType.EnkeltplassEndrePrisinfo,
    is HendelseType.ReaktiverDeltakelse,
    is HendelseType.EndreSluttarsak,
    is HendelseType.EndreUtkast,
    is HendelseType.OpprettUtkast,
    is HendelseType.AvbrytUtkast,
    is HendelseType.DeltakerSistBesokt,
    is HendelseType.SettPaaVenteliste,
    is HendelseType.TildelPlass,
    -> throw IllegalArgumentException("Skal ikke journalføre $hendelseType som endringsvedtak")

    is HendelseType.AvsluttDeltakelse -> EndringDto.AvsluttDeltakelse(
        aarsak = hendelseType.aarsak?.visningsnavn(),
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Ny sluttdato er ${hendelseType.sluttdato.formatDateWithMonthName()}",
        harDeltatt = true.tilVisningstekst(harFellesAvslutning),
        // Har fullført er alltid true fordi ellers hadde det vært avbrytDeltakelse hendelse
        harFullfort = hendelseType.harFullfort.tilVisningstekst(harFellesAvslutning),
    )

    is HendelseType.EndreAvslutning -> EndringDto.EndreAvslutning(
        aarsak = hendelseType.aarsak?.visningsnavn(),
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Avslutning endret",
        harFullfort = hendelseType.harFullfort.tilVisningstekst(true),
        sluttdato = if (hendelseType.sluttdato != null) "Sluttdato: ${hendelseType.sluttdato!!.formatDate()}" else null,
    )

    is HendelseType.AvbrytDeltakelse -> EndringDto.AvbrytDeltakelse(
        aarsak = hendelseType.aarsak?.visningsnavn(),
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Ny sluttdato er ${hendelseType.sluttdato.formatDateWithMonthName()}",
        harDeltatt = true.tilVisningstekst(harFellesAvslutning),
        // Har fullført er alltid false fordi ellers hadde det vært avsluttdeltakelse hendelse
        harFullfort = false.tilVisningstekst(harFellesAvslutning),
    )

    is HendelseType.EndreDeltakelsesmengde -> EndringDto.EndreDeltakelsesmengde(
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Deltakelsen er endret til ${
            deltakelsesmengdeTekst(
                deltakelsesprosent = hendelseType.deltakelsesprosent?.toInt(),
                dagerPerUke = hendelseType.dagerPerUke?.toInt(),
                erEnkeltplass = erEnkeltplass,
            )
        }",
        gyldigFra = hendelseType.gyldigFra,
    )

    is HendelseType.EndreSluttdato -> EndringDto.EndreSluttdato(
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Ny sluttdato er ${hendelseType.sluttdato.formatDateWithMonthName()}",
    )

    is HendelseType.EndreStartdato -> {
        val tittel =
            hendelseType.startdato?.let { "Oppstartsdato er endret til ${it.formatDateWithMonthName()}" } ?: "Oppstartsdato er fjernet"

        val sluttdato = hendelseType.sluttdato

        if (sluttdato != null) {
            EndringDto.EndreStartdatoOgVarighet(
                sluttdato = "Forventet sluttdato: ${sluttdato.formatDate()}",
                begrunnelseFraNav = hendelseType.begrunnelseFraNav,
                forslagFraArrangor = hendelseType.endringFraForslag?.let {
                    endringFraForslagToForslagDto(
                        it,
                        hendelseType.begrunnelseFraArrangor,
                        erEnkeltplass,
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
                        erEnkeltplass,
                    )
                },
                tittel = tittel,
            )
        }
    }

    is HendelseType.ForlengDeltakelse -> EndringDto.ForlengDeltakelse(
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
        tittel = "Deltakelsen er forlenget til ${hendelseType.sluttdato.formatDateWithMonthName()}",
    )

    is HendelseType.IkkeAktuell -> EndringDto.IkkeAktuell(
        aarsak = hendelseType.aarsak.visningsnavn(),
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
    )

    is HendelseType.EndreInnhold -> EndringDto.EndreInnhold(
        innhold = hendelseType.innhold.map { it.visningsnavn() },
        innholdBeskrivelse = if (tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET ||
            tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER ||
            tiltakskode.erOpplaeringstiltak()
        ) {
            hendelseType.innhold.firstOrNull { it.innholdskode == INNHOLDSKODE_ANNET }?.beskrivelse
        } else {
            null
        },
    )

    is HendelseType.EndreBakgrunnsinformasjon -> EndringDto.EndreBakgrunnsinformasjon(
        bakgrunnsinformasjon = if (hendelseType.bakgrunnsinformasjon.isNullOrEmpty()) {
            "—"
        } else {
            hendelseType.bakgrunnsinformasjon
        },
    )

    is HendelseType.LeggTilOppstartsdato -> EndringDto.LeggTilOppstartsdato(
        sluttdatoFraArrangor = hendelseType.sluttdato,
        tittel = "Oppstartsdato er ${hendelseType.startdato.formatDateWithMonthName()}",
    )

    is HendelseType.FjernOppstartsdato -> EndringDto.FjernOppstartsdato(
        begrunnelseFraNav = hendelseType.begrunnelseFraNav,
        forslagFraArrangor = hendelseType.endringFraForslag?.let {
            endringFraForslagToForslagDto(
                it,
                hendelseType.begrunnelseFraArrangor,
                erEnkeltplass,
            )
        },
    )

    is HendelseType.Avslag -> EndringDto.Avslag(
        hendelseType.aarsak.visningsnavn(),
        hendelseType.begrunnelseFraNav,
        hendelseType.vurderingFraArrangor?.let {
            EndringDto.Avslag.Vurdering(it.vurderingstype.visningsnavn(), it.begrunnelse)
        },
    )
}

fun deltakelsesmengdeTekst(
    deltakelsesprosent: Int?,
    dagerPerUke: Int?,
    erEnkeltplass: Boolean?,
): String {
    val dagerPerUkeTekst = dagerPerUkeTekst(dagerPerUke)?.lowercase()
    if (dagerPerUkeTekst != null && erEnkeltplass == true) {
        return dagerPerUkeTekst
    }
    if (dagerPerUkeTekst != null) {
        return "${deltakelsesprosent ?: 100} % fordelt på $dagerPerUkeTekst"
    }
    return "${deltakelsesprosent ?: 100} %"
}

private fun dagerPerUkeTekst(dagerPerUke: Int?): String? {
    if (dagerPerUke != null) {
        return if (dagerPerUke == 1) {
            "$dagerPerUke dag i uka"
        } else {
            "$dagerPerUke dager i uka"
        }
    }
    return null
}

private fun endringFraForslagToForslagDto(
    endring: Forslag.Endring,
    begrunnelseFraArrangor: String?,
    erEnkeltplass: Boolean?,
): ForslagDto = when (endring) {
    is Forslag.ForlengDeltakelse -> ForslagDto.ForlengDeltakelse(
        sluttdato = endring.sluttdato,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.AvsluttDeltakelse -> ForslagDto.AvsluttDeltakelse(
        aarsak = endring.aarsak?.visningsnavn(),
        sluttdato = endring.sluttdato,
        harDeltatt = endring.harDeltatt?.let { if (it) "Ja" else "Nei" },
        harFullfort = endring.harFullfort?.let { if (it) "Ja" else "Nei" },
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.Deltakelsesmengde -> ForslagDto.EndreDeltakelsesmengde(
        deltakelsesmengdeTekst = deltakelsesmengdeTekst(
            deltakelsesprosent = endring.deltakelsesprosent,
            dagerPerUke = endring.dagerPerUke,
            erEnkeltplass = erEnkeltplass,
        ),
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.IkkeAktuell -> ForslagDto.IkkeAktuell(
        aarsak = endring.aarsak.visningsnavn(),
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.Sluttdato -> ForslagDto.EndreSluttdato(
        sluttdato = endring.sluttdato,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.Startdato -> if (endring.sluttdato != null) {
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

    is Forslag.FjernOppstartsdato -> ForslagDto.FjernOppstartsdato(
        begrunnelseFraArrangor = begrunnelseFraArrangor,
    )

    is Forslag.EndreAvslutning ->
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

    is Forslag.Sluttarsak -> throw IllegalArgumentException("Skal ikke opprette endringsvedtak ved endring av sluttårsak")
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
