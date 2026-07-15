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
    constructor(model: Deltakelsesinnhold) : this(
        ledetekst = model.ledetekst,
        innhold = model.innhold.map(::InnholdResponse),
    )

    constructor(
        model: Deltakelsesinnhold,
        tiltaksInnhold: List<Innholdselement>,
    ) : this(
        ledetekst = model.ledetekst,
        innhold = fulltInnhold(model.innhold, tiltaksInnhold).map(::InnholdResponse),
    )

    data class InnholdResponse(
        val tekst: String,
        val innholdskode: String,
        val valgt: Boolean,
        val beskrivelse: String?,
    ) {
        constructor(model: Innhold) : this(
            tekst = model.tekst,
            innholdskode = model.innholdskode,
            valgt = model.valgt,
            beskrivelse = model.beskrivelse,
        )
    }

    companion object {
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
