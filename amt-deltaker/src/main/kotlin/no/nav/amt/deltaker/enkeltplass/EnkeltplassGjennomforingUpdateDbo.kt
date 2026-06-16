package no.nav.amt.deltaker.enkeltplass

import java.util.UUID

data class EnkeltplassGjennomforingUpdateDbo(
    val id: UUID,
    val arrangorId: UUID?,
)
