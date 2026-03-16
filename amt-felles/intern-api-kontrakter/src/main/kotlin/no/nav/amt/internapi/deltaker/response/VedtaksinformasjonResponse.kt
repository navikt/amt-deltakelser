package no.nav.amt.internapi.deltaker.response

import java.time.LocalDateTime

data class VedtaksinformasjonResponse(
    val fattet: LocalDateTime?,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: String,
    val opprettetAvEnhet: String,
    val sistEndret: LocalDateTime,
    val sistEndretAv: String?,
    val sistEndretAvEnhet: String?,
)
