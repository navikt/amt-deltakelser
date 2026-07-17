package no.nav.amt.deltaker.bff.commonresponse

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
            return formatWithArrangor(gjennomforing, visningsnavn)
        }

        private fun hentTiltakHosArrangorIngressTekst(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode

            hentKurstype(gjennomforing)?.let { kurstype ->
                return formatWithArrangor(gjennomforing, kurstype)
            }

            if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return formatWithArrangor(gjennomforing, gjennomforing.navn)
            }

            return formatWithArrangor(gjennomforing, TiltakskodeResponse(tiltakskode).visningsnavn)
        }

        private fun hentKladdTiltakHosArrangorTittel(gjennomforing: GjennomforingModel): String {
            val tiltakskode = gjennomforing.tiltak.tiltakskode

            if (hentKurstype(gjennomforing) == null && skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return formatWithArrangor(gjennomforing, gjennomforing.navn)
            }

            val visningsnavn = hentVisningsnavn(
                gjennomforing,
                medKurstype = gjennomforing.status != GjennomforingStatusType.KLADD,
            )
            return formatWithArrangor(gjennomforing, visningsnavn)
        }

        private fun formatWithArrangor(gjennomforing: GjennomforingModel, tekst: String): String {
            val arrangorNavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør"
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

            return TiltakskodeResponse(gjennomforing.tiltak.tiltakskode).visningsnavn
        }

        private fun hentKurstype(
            gjennomforing: GjennomforingModel,
            medKurstype: Boolean = true,
        ): String? {
            if (!medKurstype || gjennomforing.tiltak.tiltakskode != Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV) {
                return null
            }

            return gjennomforing.opplaringKategoriseringValg
                ?.valgteKategoriseringer
                ?.firstOrNull { it.representerer == OpplaringKategoriseringType.KURSTYPE_ID }
                ?.valg
                ?.values
                ?.firstOrNull()
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
