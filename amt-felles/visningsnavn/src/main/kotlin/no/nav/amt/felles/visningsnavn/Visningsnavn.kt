package no.nav.amt.felles.visningsnavn

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

const val UKJENT_ARRANGOR = "Ukjent arrangør"

fun visningsnavn(tiltakskode: Tiltakskode) = when (tiltakskode) {
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

fun hentVisningsnavnFraTiltakskode(tiltakskode: Tiltakskode): String = if (tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
    "Tilrettelagt arbeid med oppfølging"
} else {
    visningsnavn(tiltakskode)
}

fun hentKurstype(
    tiltakskode: Tiltakskode,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String? = when (tiltakskode) {
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> {
        opplaringKategoriseringValg
            ?.hentVerdier(representerer = OpplaringKategoriseringType.KURSTYPE_ID, throwIfEmpty = false)
            // minOrNull garanterer deterministisk resultat, i motsetning til firstOrNull
            ?.minOrNull()
    }

    else -> null
}

fun hentTittelTekst(
    tiltakskode: Tiltakskode,
    opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
): String = hentKurstype(tiltakskode, opplaringKategoriseringValg) ?: hentVisningsnavnFraTiltakskode(tiltakskode)

fun skalBrukeDeltakerlisteNavn(tiltakskode: Tiltakskode): Boolean = when (tiltakskode) {
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

fun tiltakHosArrangorTekst(
    tekst: String,
    arrangorNavn: String?,
    ukjentArrangorNavn: String = UKJENT_ARRANGOR,
): String = "$tekst hos ${arrangorNavn ?: ukjentArrangorNavn}"

fun tiltakHosArrangorTittel(
    tiltakskode: Tiltakskode,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
    ukjentArrangorNavn: String = UKJENT_ARRANGOR,
): String = tiltakHosArrangorTekst(
    tekst = hentTittelTekst(tiltakskode, opplaringKategoriseringValg),
    arrangorNavn = arrangorNavn,
    ukjentArrangorNavn = ukjentArrangorNavn,
)

fun tiltakHosArrangorIngressTekst(
    tiltakskode: Tiltakskode,
    deltakerlisteNavn: String,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
    brukDeltakerlisteNavn: Boolean = skalBrukeDeltakerlisteNavn(tiltakskode),
    ukjentArrangorNavn: String = UKJENT_ARRANGOR,
): String {
    val kurstype = hentKurstype(tiltakskode, opplaringKategoriseringValg)
    if (kurstype != null) {
        return tiltakHosArrangorTekst(kurstype, arrangorNavn, ukjentArrangorNavn)
    }

    if (brukDeltakerlisteNavn) {
        return tiltakHosArrangorTekst(deltakerlisteNavn, arrangorNavn, ukjentArrangorNavn)
    }

    return tiltakHosArrangorTekst(visningsnavn(tiltakskode), arrangorNavn, ukjentArrangorNavn)
}

fun kladdTiltakHosArrangorTittel(
    tiltakskode: Tiltakskode,
    deltakerlisteNavn: String,
    arrangorNavn: String?,
    erKladd: Boolean,
    opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
    brukDeltakerlisteNavn: Boolean = skalBrukeDeltakerlisteNavn(tiltakskode),
    ukjentArrangorNavn: String = UKJENT_ARRANGOR,
): String {
    val kurstype = hentKurstype(tiltakskode, opplaringKategoriseringValg)

    val tekst = when {
        kurstype == null && brukDeltakerlisteNavn -> deltakerlisteNavn
        !erKladd -> hentTittelTekst(tiltakskode, opplaringKategoriseringValg)
        else -> hentVisningsnavnFraTiltakskode(tiltakskode)
    }

    return tiltakHosArrangorTekst(tekst, arrangorNavn, ukjentArrangorNavn)
}
