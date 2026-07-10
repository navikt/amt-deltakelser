package no.nav.amt.distribusjon.hendelse.model

import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal

typealias HendelseDto = no.nav.amt.internapi.hendelse.Hendelse

fun HendelseDto.toModel(
    distribusjonskanal: Distribusjonskanal,
    manuellOppfolging: Boolean,
) = Hendelse(
    id = id,
    opprettet = opprettet,
    deltaker = deltaker,
    ansvarlig = ansvarlig,
    payload = payload,
    distribusjonskanal = distribusjonskanal,
    manuellOppfolging = manuellOppfolging,
)
