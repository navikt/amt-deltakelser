package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.felles.visningsnavn.visningsnavn
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TiltakskodeResponse(
    val kode: Tiltakskode,
    val visningsnavn: String = kode.visningsnavn(),
)
