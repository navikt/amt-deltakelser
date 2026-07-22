package no.nav.amt.felles.visningsnavn

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TiltakVisningsnavn(
    val tittel: String,
    val aktivitetskortTittel: String,
    val ingressTekst: String,
    val kladdTittel: String,
) {
    companion object {
        fun lagVisningsnavn(
            tiltakskode: Tiltakskode,
            tiltaksnavn: String,
            gjennomforingsnavn: String,
            gjennomforingType: GjennomforingType,
            erKladd: Boolean,
            arrangorNavn: String?,
            opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        ): TiltakVisningsnavn = TiltakVisningsnavn(
            tittel = lagTittel(
                tiltakskode = tiltakskode,
                tiltaksnavn = tiltaksnavn,
                arrangorNavn = arrangorNavn,
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            ),
            aktivitetskortTittel = lagAktivitetskortTittel(
                tiltakskode = tiltakskode,
                tiltaksnavn = tiltaksnavn,
                gjennomforingsnavn = gjennomforingsnavn,
                gjennomforingType = gjennomforingType,
                arrangorNavn = arrangorNavn,
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            ),
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
                erKladd = erKladd,
                arrangorNavn = arrangorNavn,
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            ),
        )

        fun lagTittel(
            tiltakskode: Tiltakskode,
            tiltaksnavn: String,
            arrangorNavn: String?,
            opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        ): String = hentTittel(
            arrangorNavn = arrangorNavn,
            tekst = hentTittelTekst(tiltakskode, tiltaksnavn, opplaringKategoriseringValg),
        )

        fun lagAktivitetskortTittel(
            tiltakskode: Tiltakskode,
            tiltaksnavn: String,
            gjennomforingsnavn: String,
            gjennomforingType: GjennomforingType,
            arrangorNavn: String?,
            opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        ): String = hentTittel(
            arrangorNavn,
            hentAktivitetskortTittelTekst(
                tiltakskode = tiltakskode,
                tiltaksnavn = tiltaksnavn,
                gjennomforingsnavn = gjennomforingsnavn,
                gjennomforingType = gjennomforingType,
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            ),
        )

        fun lagAktivitetskortTittel(
            tiltakskode: Tiltakskode,
            tiltaksnavn: String,
            gjennomforingsnavn: String,
            arrangorNavn: String?,
            opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        ): String = lagAktivitetskortTittel(
            tiltakskode = tiltakskode,
            tiltaksnavn = tiltaksnavn,
            gjennomforingsnavn = gjennomforingsnavn,
            gjennomforingType = gjennomforingTypeForAktivitetskort(tiltakskode),
            arrangorNavn = arrangorNavn,
            opplaringKategoriseringValg = opplaringKategoriseringValg,
        )

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
    }
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

    return hentTittel(arrangorNavn, TiltakVisningsnavn.visningsnavn(tiltakskode))
}

private fun hentKladdTittel(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    gjennomforingsnavn: String,
    erKladd: Boolean,
    arrangorNavn: String?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String {
    val kurstype = hentKurstype(tiltakskode, opplaringKategoriseringValg)

    val tekst = when {
        kurstype == null && skalBrukeDeltakerlisteNavn(tiltakskode) -> gjennomforingsnavn
        tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET &&
            erKladd -> tiltaksnavn
        !erKladd -> hentTittelTekst(tiltakskode, tiltaksnavn, opplaringKategoriseringValg)
        else -> hentVisningsnavnFraTiltakskode(tiltakskode)
    }

    return hentTittel(arrangorNavn, tekst)
}

private fun hentAktivitetskortTittelTekst(
    tiltakskode: Tiltakskode,
    tiltaksnavn: String,
    gjennomforingsnavn: String,
    gjennomforingType: GjennomforingType,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
): String {
    if (skalBrukeDeltakerlisteNavnIaktivitetskort(tiltakskode, gjennomforingType)) {
        return gjennomforingsnavn
    }

    return hentTittelTekst(tiltakskode, tiltaksnavn, opplaringKategoriseringValg)
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
        TiltakVisningsnavn.visningsnavn(tiltakskode)
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

private fun skalBrukeDeltakerlisteNavnIaktivitetskort(
    tiltakskode: Tiltakskode,
    gjennomforingType: GjennomforingType,
): Boolean = when (tiltakskode) {
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
    -> true

    Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    Tiltakskode.STUDIESPESIALISERING,
    Tiltakskode.FAG_OG_YRKESOPPLAERING,
    -> gjennomforingType == GjennomforingType.Gruppe

    else -> false
}

private fun gjennomforingTypeForAktivitetskort(tiltakskode: Tiltakskode): GjennomforingType =
    if (tiltakskode.erArenaEnkeltplass()) GjennomforingType.Enkeltplass else GjennomforingType.Gruppe
