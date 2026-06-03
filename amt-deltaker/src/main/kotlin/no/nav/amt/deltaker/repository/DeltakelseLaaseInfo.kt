package no.nav.amt.deltaker.repository

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Slim data for låse-sjekk i [no.nav.amt.deltaker.veileder.DeltakerLaaseService].
 * Inneholder kun feltene som trengs for å avgjøre om en deltakelse er låst.
 */
data class DeltakelseLaaseInfo(
    val id: UUID,
    val personident: String,
    val statusType: DeltakerStatus.Type,
    val statusGyldigFra: LocalDateTime,
    val vedtakFattet: LocalDateTime?,
    val innsoektDatoFraArena: LocalDate?,
)
