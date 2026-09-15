package no.nav.tiltaksarrangor.repositories.model

import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDateTime
import java.util.UUID

class NavEnhetDbo(
    val id: UUID,
    val enhetsnummer: String,
    val navn: String,
    val sistEndret: LocalDateTime,
) {
    fun toNavEnhet(): NavEnhet = NavEnhet(
        id = id,
        enhetsnummer = enhetsnummer,
        navn = navn,
    )
}
