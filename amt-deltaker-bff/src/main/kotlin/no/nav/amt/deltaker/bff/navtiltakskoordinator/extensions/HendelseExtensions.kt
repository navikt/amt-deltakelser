package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.lib.models.hendelse.Hendelse

fun Hendelse.toUlestHendelse() = this.payload.toUlestHendelseType()?.let {
    UlestHendelse(
        id = this.id,
        opprettet = this.opprettet,
        deltakerId = this.deltaker.id,
        ansvarlig = this.ansvarlig.toAnsvarligNavnOgEnhet(),
        hendelse = it,
    )
}
