package no.nav.amt.deltaker.bff.tiltakskoordinator.model

import java.util.UUID

data class Tiltakskoordinator(
    val id: UUID,
    val navn: String,
    val erAktiv: Boolean,
    val kanFjernes: Boolean,
)
