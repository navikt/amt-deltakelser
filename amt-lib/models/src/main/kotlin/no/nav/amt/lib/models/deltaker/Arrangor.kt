package no.nav.amt.lib.models.deltaker

import java.util.UUID

data class Arrangor(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangorId: UUID?,
)
