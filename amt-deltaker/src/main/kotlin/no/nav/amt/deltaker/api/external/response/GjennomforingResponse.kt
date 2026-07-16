package no.nav.amt.deltaker.api.external.response

import no.nav.amt.deltaker.bff.commonresponse.TiltakskodeResponse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

data class GjennomforingResponse(
    val id: UUID,
    val navn: String,
    val type: String, // Arena type
    val tiltakskode: Tiltakskode,
    val tiltakstypeNavn: String,
    val arrangor: ArrangorResponse,
) {
    @Suppress("unused") // serialiseres
    val tiltakskodeResponse: TiltakskodeResponse = TiltakskodeResponse(tiltakskode)
}
