package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig

fun HendelseAnsvarlig.toAnsvarligNavnOgEnhet(): AnsvarligNavnOgEnhet? = when (this) {
    is HendelseAnsvarlig.NavTiltakskoordinator -> AnsvarligNavnOgEnhet(endretAvNavn = navn, endretAvEnhet = enhet.navn)
    is HendelseAnsvarlig.NavVeileder -> AnsvarligNavnOgEnhet(endretAvNavn = navn)
    else -> null
}
