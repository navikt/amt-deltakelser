package no.nav.amt.deltaker.extensions

import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.lib.models.deltaker.Vedtak

fun Vedtak.tilVedtaksInformasjon(): Vedtaksinformasjon = Vedtaksinformasjon(
    fattet = fattet,
    fattetAvNav = fattetAvNav,
    opprettet = opprettet,
    opprettetAv = opprettetAv,
    opprettetAvEnhet = opprettetAvEnhet,
    sistEndret = sistEndret,
    sistEndretAv = sistEndretAv,
    sistEndretAvEnhet = sistEndretAvEnhet,
)
