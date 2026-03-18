package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.deltaker.model.VedtaksinformasjonModel
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDateTime
import java.util.UUID

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
        fun fromVedtak(
            vedtak: Vedtak,
            ansatte: Map<UUID, NavAnsatt>,
            vedtakSistEndretEnhet: NavEnhet?,
        ) = with(vedtak) {
            VedtaksinformasjonResponse(
                fattet = fattet,
                fattetAvNav = fattetAvNav,
                opprettet = opprettet,
                opprettetAv = ansatte[opprettetAv]?.navn ?: opprettetAv.toString(),
                sistEndret = sistEndret,
                sistEndretAv = ansatte[sistEndretAv]?.navn ?: sistEndretAv.toString(),
                sistEndretAvEnhet = vedtakSistEndretEnhet?.navn ?: sistEndretAvEnhet.toString(),
            )
        }

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
