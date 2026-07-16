package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class VisningsnavnResponse(
    val tiltakHosArrangorTittel: String,
    val tiltakHosArrangorIngressTekst: String,
) {
    constructor(model: GjennomforingModel) : this(
        tiltakHosArrangorTittel = hentTiltakHosArrangorTittel(model),
        tiltakHosArrangorIngressTekst = hentTiltakHosArrangorIngressTekst(model),
    )

    companion object {
        private fun hentTiltakHosArrangorTittel(model: GjennomforingModel): String {
            val arrangorNavn = model.arrangor?.navn ?: "Ukjent arrangør"
            val tiltakskode = model.tiltak.tiltakskode
            val kurstype = hentKurstype(model)

            if (kurstype != null) {
                return "$kurstype hos $arrangorNavn"
            }

            if (tiltakskode == Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) {
                return "Tilrettelagt arbeid med oppfølging hos $arrangorNavn"
            }

            return "${TiltakskodeResponse(tiltakskode).visningsnavn} hos $arrangorNavn"
        }

        private fun hentTiltakHosArrangorIngressTekst(model: GjennomforingModel): String {
            val arrangorNavn = model.arrangor?.navn ?: "Ukjent arrangør"
            val tiltakskode = model.tiltak.tiltakskode
            val kurstype = hentKurstype(model)

            if (kurstype != null) {
                return "$kurstype hos $arrangorNavn"
            }

            if (skalBrukeDeltakerlisteNavn(tiltakskode)) {
                return "${model.navn} hos $arrangorNavn"
            }

            return "${TiltakskodeResponse(tiltakskode).visningsnavn} hos $arrangorNavn"
        }

        private fun hentKurstype(model: GjennomforingModel): String? {
            if (model.tiltak.tiltakskode != Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV) {
                return null
            }

            return model.opplaringKategoriseringValg
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