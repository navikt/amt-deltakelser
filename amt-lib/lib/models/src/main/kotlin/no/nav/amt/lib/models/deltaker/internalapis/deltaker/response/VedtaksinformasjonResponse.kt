package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response

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