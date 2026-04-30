package no.nav.amt.deltaker.api

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.toInnhold

fun List<InnholdsElementRequest>.toInnholdModel(deltaker: Deltaker) = this.mapNotNull { valgtInnholdElement ->
    val tiltaksinnhold = getInnholdselementer(
        innholdselementer = deltaker.deltakerliste.tiltakstype.innhold
            ?.innholdselementer,
        tiltakstype = deltaker.deltakerliste.tiltakstype.tiltakskode,
    ).find { it.innholdskode == valgtInnholdElement.innholdskode }
    if (valgtInnholdElement.innholdskode == annetInnholdselement.innholdskode) {
        tiltaksinnhold?.toInnhold(true, valgtInnholdElement.beskrivelse)
    } else {
        tiltaksinnhold?.toInnhold(valgt = true)
    }
}
