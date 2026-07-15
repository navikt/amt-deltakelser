package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.model.VedtaksinformasjonModel
import java.time.LocalDateTime

data class VedtaksinformasjonResponse(
    val fattet: LocalDateTime?,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: String,
    val sistEndret: LocalDateTime,
    val sistEndretAv: String?,
    val sistEndretAvEnhet: String?,
) {
    constructor(model: VedtaksinformasjonModel) : this(
        fattet = model.fattet,
        fattetAvNav = model.fattetAvNav,
        opprettet = model.opprettet,
        opprettetAv = model.opprettetAv,
        sistEndret = model.sistEndret,
        sistEndretAv = model.sistEndretAv,
        sistEndretAvEnhet = model.sistEndretAvEnhet,
    )
}
