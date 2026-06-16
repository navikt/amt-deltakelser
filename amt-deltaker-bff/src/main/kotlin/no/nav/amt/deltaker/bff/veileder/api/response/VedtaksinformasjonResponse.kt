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
    companion object {
        fun fromVedtak(vedtak: VedtaksinformasjonModel) = with(vedtak) {
            VedtaksinformasjonResponse(
                fattet = fattet,
                fattetAvNav = fattetAvNav,
                opprettet = opprettet,
                opprettetAv = vedtak.opprettetAv,
                sistEndret = sistEndret,
                sistEndretAv = vedtak.sistEndretAv,
                sistEndretAvEnhet = vedtak.sistEndretAvEnhet,
            )
        }
    }
}
