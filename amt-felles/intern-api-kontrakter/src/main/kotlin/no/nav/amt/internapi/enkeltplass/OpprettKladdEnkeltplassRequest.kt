package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class OpprettKladdEnkeltplassRequest(
    val tiltakskode: Tiltakskode,
    val personident: String,
)
