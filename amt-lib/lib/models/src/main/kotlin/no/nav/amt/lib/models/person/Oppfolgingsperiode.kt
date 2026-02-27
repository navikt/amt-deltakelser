package no.nav.amt.lib.models.person

import no.nav.amt.lib.models.person.extensions.isBetweenEndExclusive
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Oppfolgingsperiode(
    val id: UUID,
    val startdato: LocalDateTime,
    val sluttdato: LocalDateTime?,
) {
    fun erAktiv(): Boolean = LocalDate.now().isBetweenEndExclusive(
        startdato.toLocalDate(),
        sluttdato?.toLocalDate(),
    )
}
