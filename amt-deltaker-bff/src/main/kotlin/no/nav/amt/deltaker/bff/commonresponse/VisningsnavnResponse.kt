package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class VisningsnavnResponse(
    val tiltakHosArrangorTittel: String,
    val tiltakHosArrangorIngressTekst: String,
    val kladdTiltakHosArrangorTittel: String,
) {
    constructor(gjennomforing: GjennomforingModel) : this(
        tiltakHosArrangorTittel = hentTiltakHosArrangorTittel(gjennomforing),
        tiltakHosArrangorIngressTekst = hentTiltakHosArrangorIngressTekst(gjennomforing),
        kladdTiltakHosArrangorTittel = hentKladdTiltakHosArrangorTittel(gjennomforing),
    )

    companion object {
        private fun hentTiltakHosArrangorTittel(gjennomforing: GjennomforingModel): String =
            tiltakHosArrangorTekst(gjennomforing.arrangor, hentTittelTekst(gjennomforing))

        private fun hentTiltakHosArrangorIngressTekst(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode

            val kurstype = hentKurstype(gjennomforing)
            if (kurstype != null) {
                return tiltakHosArrangorTekst(gjennomforing.arrangor, kurstype)
            }

            if (tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET) {
                return tiltakHosArrangorTekst(gjennomforing.arrangor, gjennomforing.tiltak.navn)
            }

            if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return tiltakHosArrangorTekst(gjennomforing)
            }

            return tiltakHosArrangorTekst(gjennomforing.arrangor, visningsnavn(tiltakskode))
        }

        private fun hentKladdTiltakHosArrangorTittel(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode
            val kurstype = hentKurstype(gjennomforing)

            val tekst = when {
                kurstype == null && skalBrukeDeltakerlisteNavn(tiltakskode) -> gjennomforing.navn
                tiltakskode == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET &&
                    gjennomforing.status == GjennomforingStatusType.KLADD -> gjennomforing.tiltak.navn
                gjennomforing.status != GjennomforingStatusType.KLADD -> hentTittelTekst(gjennomforing)
                else -> hentVisningsnavnFraTiltakskode(tiltakskode)
            }

            return tiltakHosArrangorTekst(gjennomforing.arrangor, tekst)
        }

        private fun tiltakHosArrangorTekst(gjennomforing: GjennomforingModel): String =
            tiltakHosArrangorTekst(gjennomforing.arrangor, gjennomforing.navn)

        private fun tiltakHosArrangorTekst(
            arrangor: ArrangorModel?,
            tekst: String,
        ): String {
            val arrangorNavn = arrangor?.navn ?: "Ukjent arrangør"
            return "$tekst hos $arrangorNavn"
        }

        private fun hentTittelTekst(gjennomforing: GjennomforingModel): String =
            hentKurstype(gjennomforing) ?: hentTittelFraTiltak(gjennomforing)

        private fun hentKurstype(gjennomforing: GjennomforingModel): String? = when (gjennomforing.tiltak.tiltakskode) {
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> {
                gjennomforing.opplaringKategoriseringValg
                    ?.hentVerdier(representerer = OpplaringKategoriseringType.KURSTYPE_ID, throwIfEmpty = false)
                    // minOrNull garanterer deterministisk resultat, i motsetning til firstOrNull
                    ?.minOrNull()
            }

            else -> null
        }

        private fun hentTittelFraTiltak(gjennomforing: GjennomforingModel): String =
            when (gjennomforing.tiltak.tiltakskode) {
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Tilrettelagt arbeid"
                else -> hentVisningsnavnFraTiltak(gjennomforing)
            }

        private fun hentVisningsnavnFraTiltak(gjennomforing: GjennomforingModel): String =
            when (gjennomforing.tiltak.tiltakskode) {
                Tiltakskode.JOBBKLUBB,
                Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
                -> hentVisningsnavnFraTiltakskode(gjennomforing.tiltak.tiltakskode)

                else -> gjennomforing.tiltak.navn
            }

        private fun hentVisningsnavnFraTiltakskode(tiltakskode: Tiltakskode): String =
            if (tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
                "Tilrettelagt arbeid med oppfølging"
            } else {
                visningsnavn(tiltakskode)
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
    }
}
