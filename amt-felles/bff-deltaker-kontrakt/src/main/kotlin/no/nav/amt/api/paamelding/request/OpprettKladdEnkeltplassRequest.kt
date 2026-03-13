package no.nav.amt.api.paamelding.request

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class OpprettKladdEnkeltplassRequest(
    val tiltakskode: Tiltakskode,
    val personident: String,
)
