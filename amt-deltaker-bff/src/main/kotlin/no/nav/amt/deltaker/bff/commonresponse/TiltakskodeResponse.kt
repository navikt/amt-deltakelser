package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TiltakskodeResponse(
    val kode: Tiltakskode,
    val visningsnavn: String = visningsnavn(kode),
)
