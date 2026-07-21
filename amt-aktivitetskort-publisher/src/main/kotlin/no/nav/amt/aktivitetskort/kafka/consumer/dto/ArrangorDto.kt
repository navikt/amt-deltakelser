package no.nav.amt.aktivitetskort.kafka.consumer.dto

import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.utils.toTitleCase
import java.util.UUID

data class ArrangorDto(
    val id: UUID,
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetArrangorId: UUID?,
) {
    fun toModel() = Arrangor(
        this.id,
        this.organisasjonsnummer,
        toTitleCase(navn),
        overordnetArrangorId,
    )
}
