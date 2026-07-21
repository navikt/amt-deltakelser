package no.nav.amt.felles.visningsnavn

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TiltakVisningsnavn(
    val tittel: String,
    val ingressTekst: String,
    val kladdTittel: String,
)

fun lagVisningsnavn(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    gjennomforingsnavn: String,
    status: GjennomforingStatusType,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
): TiltakVisningsnavn = TiltakVisningsnavn(
    tittel = hentTittel(arrangorNavn, hentTittelTekst(tiltakskode, tiltaksnavn, opplaringKategoriseringValg)),
    ingressTekst = hentIngressTekst(
        tiltakskode = tiltakskode,
        tiltaksnavn = tiltaksnavn,
        gjennomforingsnavn = gjennomforingsnavn,
        arrangorNavn = arrangorNavn,
        opplaringKategoriseringValg = opplaringKategoriseringValg,
    ),
    kladdTittel = hentKladdTittel(
        tiltakskode = tiltakskode,
        tiltaksnavn = tiltaksnavn,
        gjennomforingsnavn = gjennomforingsnavn,
        status = status,
        arrangorNavn = arrangorNavn,
        opplaringKategoriseringValg = opplaringKategoriseringValg,
    ),
)

fun Tiltakskode.visningsnavn() = when (this) {
    Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> "Arbeidsforberedende trening"
    Tiltakskode.ARBEIDSRETTET_REHABILITERING -> "Arbeidsrettet rehabilitering"
    Tiltakskode.AVKLARING -> "Avklaring"
    Tiltakskode.OPPFOLGING -> "Oppfølging"
    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Varig tilrettelagt arbeid"
    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> "Digitalt jobbsøkerkurs"
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
    Tiltakskode.JOBBKLUBB -> "Jobbsøkerkurs"
    Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring (enkeltplass)"
    Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring (enkeltplass)"
    Tiltakskode.HOYERE_UTDANNING -> "Høyere utdanning"
    Tiltakskode.ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> "Norskopplæring, grunnleggende ferdigheter og FOV"
    Tiltakskode.STUDIESPESIALISERING -> "Studiespesialisering"
    Tiltakskode.FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING -> "Høyere yrkesfaglig utdanning"
    Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER -> "Tilrettelagt arbeid i ordinær virksomhet"
}

private fun hentIngressTekst(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    gjennomforingsnavn: String,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String {
    val kurstype = hentKurstype(tiltakskode, opplaringKategoriseringValg)
    if (kurstype != null) {
        return hentTittel(arrangorNavn, kurstype)
    }

    if (tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET) {
        return hentTittel(arrangorNavn, tiltaksnavn)
    }

    if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
        return hentTittel(arrangorNavn, gjennomforingsnavn)
    }

    return hentTittel(arrangorNavn, tiltakskode.visningsnavn())
}

private fun hentKladdTittel(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    gjennomforingsnavn: String,
    status: GjennomforingStatusType,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String {
    val kurstype = hentKurstype(tiltakskode, opplaringKategoriseringValg)

    val tekst = when {
        kurstype == null && skalBrukeDeltakerlisteNavn(tiltakskode) -> gjennomforingsnavn
        tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET &&
            status == GjennomforingStatusType.KLADD -> tiltaksnavn
        status != GjennomforingStatusType.KLADD -> hentTittelTekst(tiltakskode, tiltaksnavn, opplaringKategoriseringValg)
        else -> hentVisningsnavnFraTiltakskode(tiltakskode)
    }

    return hentTittel(arrangorNavn, tekst)
}

private fun hentTittel(
    arrangorNavn: String?,
    tekst: String,
): String {
    val navn = arrangorNavn ?: "Ukjent arrangør"
    return "$tekst hos $navn"
}

private fun hentTittelTekst(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String = hentKurstype(tiltakskode, opplaringKategoriseringValg) ?: hentTittelFraTiltak(tiltakskode, tiltaksnavn)

private fun hentKurstype(
    tiltakskode: Tiltakskode,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String? = when (tiltakskode) {
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> {
        opplaringKategoriseringValg
            ?.hentVerdier(representerer = OpplaringKategoriseringType.KURSTYPE_ID, throwIfEmpty = false)
            ?.minOrNull()
    }

    else -> null
}

private fun hentTittelFraTiltak(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
): String = when (tiltakskode) {
    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Tilrettelagt arbeid"
    else -> hentVisningsnavnFraTiltak(tiltakskode, tiltaksnavn)
}

private fun hentVisningsnavnFraTiltak(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
): String = when (tiltakskode) {
    Tiltakskode.JOBBKLUBB,
    Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
    -> hentVisningsnavnFraTiltakskode(tiltakskode)

    else -> tiltaksnavn
}

private fun hentVisningsnavnFraTiltakskode(tiltakskode: Tiltakskode): String =
    if (tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
        "Tilrettelagt arbeid med oppfølging"
    } else {
        tiltakskode.visningsnavn()
    }

private fun skalBrukeDeltakerlisteNavn(tiltakskode: Tiltakskode): Boolean = when (tiltakskode) {
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
    Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    Tiltakskode.STUDIESPESIALISERING,
    Tiltakskode.FAG_OG_YRKESOPPLAERING,
    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
    Tiltakskode.HOYERE_UTDANNING,
    -> true

    else -> false
}
