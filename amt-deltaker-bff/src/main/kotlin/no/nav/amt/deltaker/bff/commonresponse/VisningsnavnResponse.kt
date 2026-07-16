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
            val arrangorNavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør"
            val tiltakskode = gjennomforing.tiltak.tiltakskode
            val kurstype = hentKurstype(gjennomforing, medKurstype)

            if (kurstype != null) {
                return "$kurstype hos $arrangorNavn"
            }

            if (tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
                return "Tilrettelagt arbeid med oppfølging hos $arrangorNavn"
            }

            return "${TiltakskodeResponse(tiltakskode).visningsnavn} hos $arrangorNavn"
        }

        private fun hentTiltakHosArrangorIngressTekst(gjennomforing: GjennomforingModel): String {
            val arrangorNavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør"
            val tiltakskode = gjennomforing.tiltak.tiltakskode
            val kurstype = hentKurstype(gjennomforing)

            if (kurstype != null) {
                return "$kurstype hos $arrangorNavn"
            }

            if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return "${gjennomforing.navn} hos $arrangorNavn"
            }

            return "${TiltakskodeResponse(tiltakskode).visningsnavn} hos $arrangorNavn"
        }

        private fun hentKladdTiltakHosArrangorTittel(gjennomforing: GjennomforingModel): String {
            val arrangorNavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør"
            val tiltakskode = gjennomforing.tiltak.tiltakskode
            val kurstype = hentKurstype(gjennomforing)

            if (kurstype == null && skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return "${gjennomforing.navn} hos $arrangorNavn"
            }

            return hentTiltakHosArrangorTittel(
                gjennomforing = gjennomforing,
                medKurstype = gjennomforing.status != GjennomforingStatusType.KLADD,
            )
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
