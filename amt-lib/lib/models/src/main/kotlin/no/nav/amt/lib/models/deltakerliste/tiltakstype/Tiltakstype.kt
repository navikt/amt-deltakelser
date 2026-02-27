package no.nav.amt.lib.models.deltakerliste.tiltakstype

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.HOYERE_UTDANNING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.STUDIESPESIALISERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.JOBBKLUBB
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK


import java.util.UUID

data class Tiltakstype(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
    val innsatsgrupper: Set<Innsatsgruppe>,
    val innhold: DeltakerRegistreringInnhold?,
) {
    val visningsnavn get() = if (navn == "Jobbklubb") {
        "Jobbsøkerkurs"
    } else {
        navn
    }

    companion object {
        val kursTiltak = setOf(
            GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            GRUPPE_FAG_OG_YRKESOPPLAERING,
            JOBBKLUBB,
        )
        val enkeltplassTiltak = setOf(
            HOYERE_UTDANNING,
            ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
        )
        val opplaeringsTiltak = setOf(
            ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            HOYERE_UTDANNING,
            GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            GRUPPE_FAG_OG_YRKESOPPLAERING,
            ARBEIDSMARKEDSOPPLAERING,
            NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            STUDIESPESIALISERING,
            FAG_OG_YRKESOPPLAERING,
            HOYERE_YRKESFAGLIG_UTDANNING
        )
        
        val tiltakUtenDeltakerAdresseDeling = setOf(
            DIGITALT_OPPFOLGINGSTILTAK,
            JOBBKLUBB,
            GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            GRUPPE_FAG_OG_YRKESOPPLAERING,
        )
    }

    @Deprecated("Utrygg sjekk av kurstiltak. Må erstattes med å sjekke oppstartstype på tiltak")
    fun erKurs() = this.tiltakskode in kursTiltak

    fun erEnkeltplass() = this.tiltakskode in enkeltplassTiltak
    val erOpplaeringstiltak = tiltakskode in opplaeringsTiltak
    val harDeltakelsesmengde = tiltakskode in setOf(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING, Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET)
    val adresseKanDelesMedArrangor = tiltakskode !in tiltakUtenDeltakerAdresseDeling
}
