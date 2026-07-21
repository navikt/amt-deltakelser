package no.nav.amt.aktivitetskort.domain

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class Tiltak(
    val navn: String,
    val tiltakskode: Tiltakskode,
)
