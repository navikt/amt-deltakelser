package no.nav.amt.lib.models.kafka

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TiltakPayload(
    val navn: String,
    val tiltakskode: Tiltakskode,
)
