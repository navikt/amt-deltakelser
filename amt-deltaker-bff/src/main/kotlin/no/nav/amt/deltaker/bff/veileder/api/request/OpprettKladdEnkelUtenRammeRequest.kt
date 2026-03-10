package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class OpprettKladdEnkelUtenRammeRequest(
    val personident: String,
    val tiltakskode: Tiltakskode,
)
