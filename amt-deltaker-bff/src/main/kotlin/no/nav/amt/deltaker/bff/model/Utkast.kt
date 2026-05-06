package no.nav.amt.deltaker.bff.model

import java.util.UUID

data class Utkast(
    val deltakerId: UUID,
    val pamelding: Pamelding,
    val godkjentAvNav: Boolean,
)
