package no.nav.amt.lib.models.kafka

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class Tiltak(
    val navn: String,
    val tiltakskode: Tiltakskode,
)
