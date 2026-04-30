package no.nav.amt.deltaker.clients.oppfolgingstilfelle

import java.time.LocalDate

data class OppfolgingstilfelleDto(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
) {
    fun gyldigForDato(dato: LocalDate): Boolean = dato in start..end
}
