package no.nav.amt.aktivitetskort.client.response

import no.nav.amt.aktivitetskort.domain.Arrangor
import java.util.UUID

data class ArrangorMedOverordnetArrangorResponse(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangor: Arrangor?,
)
