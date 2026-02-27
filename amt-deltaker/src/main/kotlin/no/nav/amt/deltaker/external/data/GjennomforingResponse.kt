package no.nav.amt.deltaker.external.data

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

data class GjennomforingResponse(
    val id: UUID,
    val navn: String,
    val type: String, // Arena type
    val tiltakskode: Tiltakskode,
    val tiltakstypeNavn: String,
    val arrangor: ArrangorResponse,
)
