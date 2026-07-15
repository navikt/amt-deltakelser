package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement

data class DeltakelsesinnholdResponse(
    val ledetekst: String?,
    val innhold: List<InnholdResponse>,
) {
    data class InnholdResponse(
        val tekst: String,
        val innholdskode: String,
        val valgt: Boolean,
        val beskrivelse: String?,
    ) {
        companion object {
            fun fromInnhold(innhold: Innhold) = InnholdResponse(
                tekst = innhold.tekst,
                innholdskode = innhold.innholdskode,
                valgt = innhold.valgt,
                beskrivelse = innhold.beskrivelse,
            )
        }
    }

    companion object {
        fun fromDeltakelsesinnhold(
            deltakelsesinnhold: Deltakelsesinnhold,
            tiltaksInnhold: List<Innholdselement>?,
        ) = DeltakelsesinnholdResponse(
            ledetekst = deltakelsesinnhold.ledetekst,
            innhold = fulltInnhold(deltakelsesinnhold.innhold, tiltaksInnhold ?: emptyList()).map { InnholdResponse.fromInnhold(it) },
        )

        fun fulltInnhold(
            valgtInnhold: List<Innhold>,
            innholdselementer: List<Innholdselement>,
        ): List<Innhold> = innholdselementer
            .asSequence()
            .filterNot { it.innholdskode in valgtInnhold.map { vi -> vi.innholdskode } }
            .map { it.toInnhold() }
            .plus(valgtInnhold)
            .sortedWith(sortertAlfabetiskMedAnnetSist())
            .toList()

        private fun sortertAlfabetiskMedAnnetSist() = compareBy<Innhold> {
            it.tekst == annetInnholdselement.tekst
        }.thenBy {
            it.tekst
        }
    }
}
