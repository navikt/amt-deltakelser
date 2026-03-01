package no.nav.amt.deltaker.bff.tiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.lib.models.hendelse.HendelseAnsvarlig

fun HendelseAnsvarlig.toAnsvarligNavnOgEnhet(): AnsvarligNavnOgEnhet? = when (this) {
    is HendelseAnsvarlig.NavTiltakskoordinator -> AnsvarligNavnOgEnhet(endretAvNavn = navn, endretAvEnhet = enhet.navn)
    is HendelseAnsvarlig.NavVeileder -> AnsvarligNavnOgEnhet(endretAvNavn = navn)
    else -> null
}
