package no.nav.amt.aktivitetskort.domain

import java.util.UUID

data class Arrangor(
    val id: UUID,
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetArrangorId: UUID?,
)
