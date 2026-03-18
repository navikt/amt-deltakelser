package no.nav.amt.internapi.paamelding.request

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class OpprettKladdEnkeltplassRequest(
    val tiltakskode: Tiltakskode,
    val personident: String,
)
