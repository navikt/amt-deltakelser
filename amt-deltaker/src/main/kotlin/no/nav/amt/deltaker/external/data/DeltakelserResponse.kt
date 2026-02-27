package no.nav.amt.deltaker.external.data

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class DeltakelserResponse(
    val aktive: List<DeltakerKort>,
    val historikk: List<DeltakerKort>,
) {
    data class Tiltakstype(
        val navn: String,
        val tiltakskode: Tiltakskode,
    )
}
