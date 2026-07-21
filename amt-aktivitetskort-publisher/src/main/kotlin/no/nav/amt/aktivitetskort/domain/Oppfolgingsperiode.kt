package no.nav.amt.aktivitetskort.domain

import java.time.LocalDateTime
import java.util.UUID

data class Oppfolgingsperiode(
    val id: UUID,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime? = null,
)
