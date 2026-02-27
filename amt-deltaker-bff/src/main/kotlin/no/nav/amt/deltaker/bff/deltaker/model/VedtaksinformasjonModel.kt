package no.nav.amt.deltaker.bff.deltaker.model

import java.time.LocalDateTime

data class VedtaksinformasjonModel(
    val fattet: LocalDateTime?,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: String,
    val opprettetAvEnhet: String,
    val sistEndret: LocalDateTime,
    val sistEndretAv: String?,
    val sistEndretAvEnhet: String?,
)
