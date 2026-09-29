package no.nav.amt.aktivitetskort.domain

import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltak: Tiltak,
    val navn: String,
    val arrangorId: UUID,
)
