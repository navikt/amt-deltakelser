package no.nav.amt.deltaker.apiclients.oppfolgingstilfelle

import java.time.LocalDate

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
) {
    fun gyldigForDato(dato: LocalDate): Boolean = dato in start..end
}
