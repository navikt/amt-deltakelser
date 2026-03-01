package no.nav.amt.lib.models.person.dto

import no.nav.amt.lib.models.person.NavEnhet
import java.util.UUID

data class NavEnhetDto(
    val id: UUID,
    val enhetId: String,
    val navn: String,
) {
    fun toModel() = NavEnhet(
        id = id,
        enhetsnummer = enhetId,
        navn = navn,
    )
}
