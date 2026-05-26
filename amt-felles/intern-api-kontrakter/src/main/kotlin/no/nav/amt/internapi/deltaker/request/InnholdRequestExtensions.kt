package no.nav.amt.internapi.deltaker.request

import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

// Hører egentlig hjemme i amt-deltaker men flyttes til lib
fun List<InnholdsElementRequest>.toInnholdModel(tiltak: Tiltakstype) = this.mapNotNull { valgtInnholdElement ->
    val tiltaksinnhold = getInnholdselementer(
        innholdselementer = tiltak.innhold
            ?.innholdselementer,
        tiltakstype = tiltak.tiltakskode,
    ).find { it.innholdskode == valgtInnholdElement.innholdskode }
    if (valgtInnholdElement.innholdskode == annetInnholdselement.innholdskode) {
        tiltaksinnhold?.toInnhold(valgt = true, beskrivelse = valgtInnholdElement.beskrivelse)
    } else {
        tiltaksinnhold?.toInnhold(valgt = true)
    }
}
