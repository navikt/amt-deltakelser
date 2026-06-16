package no.nav.amt.deltaker.api

import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

fun validerKladdInnhold(
    innhold: List<InnholdsElementRequest>,
    tiltaksinnhold: DeltakerRegistreringInnhold?,
    tiltakstype: Tiltakskode,
) {
    validerInnhold(tiltakstype, innhold, tiltaksinnhold) { innholdskoder ->
        innhold.forEach {
            require(it.innholdskode in innholdskoder) { "Ugyldig innholdskode: ${it.innholdskode}" }

            if (it.innholdskode != annetInnholdselement.innholdskode) {
                require(it.beskrivelse == null) {
                    "Innholdskode: ${it.innholdskode} kan ikke ha en beskrivelse"
                }
            }
        }
    }
}

private fun validerInnhold(
    tiltakstype: Tiltakskode,
    valgteInnholdselementer: List<InnholdsElementRequest>,
    tiltaksinnhold: DeltakerRegistreringInnhold?,
    valider: (innholdskoder: List<String>) -> Unit,
) {
    val muligeInnholdskoderForTiltak = getInnholdselementer(tiltaksinnhold?.innholdselementer, tiltakstype)
        .map { it.innholdskode }

    if (muligeInnholdskoderForTiltak.isEmpty()) {
        require(valgteInnholdselementer.isEmpty()) { "Et tiltak uten innholdselementer kan ikke ha noe innhold" }
    } else {
        valider(muligeInnholdskoderForTiltak)
    }
}
