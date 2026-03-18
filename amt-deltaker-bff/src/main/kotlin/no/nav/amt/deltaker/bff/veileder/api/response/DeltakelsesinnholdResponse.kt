package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.toInnhold
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement

data class DeltakelsesinnholdResponse(
    val ledetekst: String?,
    val innhold: List<Innhold>,
) {
    companion object {
        fun fromDeltakelsesinnhold(
            deltakelsesinnhold: Deltakelsesinnhold,
            tiltaksInnhold: List<Innholdselement>?,
        ) = DeltakelsesinnholdResponse(
            ledetekst = deltakelsesinnhold.ledetekst,
            innhold = fulltInnhold(deltakelsesinnhold.innhold, tiltaksInnhold ?: emptyList()),
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
