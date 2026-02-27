package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

data class Vedtak(
    val id: UUID,
    val deltakerId: UUID,
    val fattet: LocalDateTime?,
    val gyldigTil: LocalDateTime?,
    val deltakerVedVedtak: DeltakerVedVedtak,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: UUID,
    val opprettetAvEnhet: UUID,
    val sistEndret: LocalDateTime,
    val sistEndretAv: UUID,
    val sistEndretAvEnhet: UUID,
)
