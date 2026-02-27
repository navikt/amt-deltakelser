package no.nav.amt.deltaker.deltaker.model

import java.time.LocalDateTime
import java.util.UUID

data class Vedtaksinformasjon(
    val fattet: LocalDateTime?,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: UUID,
    val opprettetAvEnhet: UUID,
    val sistEndret: LocalDateTime,
    val sistEndretAv: UUID,
    val sistEndretAvEnhet: UUID,
)
