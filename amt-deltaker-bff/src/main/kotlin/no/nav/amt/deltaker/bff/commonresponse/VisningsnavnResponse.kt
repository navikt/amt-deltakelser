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
        private fun hentTiltakHosArrangorTittel(
            gjennomforing: GjennomforingModel,
            medKurstype: Boolean = true,
        ): String {
            val visningsnavn = hentVisningsnavn(gjennomforing, medKurstype = medKurstype)
            return tiltakHosArrangorTekst(gjennomforing.arrangor, visningsnavn)
        }

        private fun hentTiltakHosArrangorIngressTekst(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode

            val kurstype = hentKurstype(gjennomforing)
            if (kurstype != null) {
                return tiltakHosArrangorTekst(gjennomforing.arrangor, kurstype)
            }

            if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return tiltakHosArrangorTekst(gjennomforing)
            }

            return tiltakHosArrangorTekst(gjennomforing.arrangor, visningsnavn(tiltakskode))
        }

        private fun hentKladdTiltakHosArrangorTittel(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode

            if (hentKurstype(gjennomforing) == null && skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return tiltakHosArrangorTekst(gjennomforing)
            }

            val visningsnavn = hentVisningsnavn(
                gjennomforing,
                medKurstype = gjennomforing.status != GjennomforingStatusType.KLADD,
            )
            return tiltakHosArrangorTekst(gjennomforing.arrangor, visningsnavn)
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

        private fun hentVisningsnavn(
            gjennomforing: GjennomforingModel,
            medKurstype: Boolean = true,
        ): String {
            hentKurstype(gjennomforing, medKurstype)?.let { return it }

            if (gjennomforing.tiltak.tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
                return "Tilrettelagt arbeid med oppfølging"
            }

            return visningsnavn(gjennomforing.tiltak.tiltakskode)
        }

        private fun hentKurstype(
            gjennomforing: GjennomforingModel,
            medKurstype: Boolean = true,
        ): String? {
            if (!medKurstype || gjennomforing.tiltak.tiltakskode != Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV) {
                return null
            }

            return gjennomforing.opplaringKategoriseringValg
                ?.hentVerdier(representerer = OpplaringKategoriseringType.KURSTYPE_ID, throwIfEmpty = false)
                // minOrNull garanterer deterministisk resultat, i motsetning til firstOrNull
                ?.minOrNull()
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
