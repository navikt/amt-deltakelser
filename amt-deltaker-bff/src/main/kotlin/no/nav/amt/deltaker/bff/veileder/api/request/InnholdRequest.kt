package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.toInnhold

// Denne skal sannsynligvis slettes. Samme kode ligger i amt-deltaker
fun List<InnholdsElementRequest>.toInnholdModel(deltaker: Deltaker) = this.mapNotNull { valgtInnholdElement ->
    val tiltaksinnhold = getInnholdselementer(
        deltaker.deltakerliste.tiltak.innhold
            ?.innholdselementer,
        deltaker.deltakerliste.tiltak.tiltakskode,
    ).find { it.innholdskode == valgtInnholdElement.innholdskode }
    if (valgtInnholdElement.innholdskode == annetInnholdselement.innholdskode) {
        tiltaksinnhold?.toInnhold(true, valgtInnholdElement.beskrivelse)
    } else {
        tiltaksinnhold?.toInnhold(valgt = true)
    }
}
