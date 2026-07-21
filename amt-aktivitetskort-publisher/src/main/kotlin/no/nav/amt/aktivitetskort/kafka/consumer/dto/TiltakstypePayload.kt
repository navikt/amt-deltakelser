package no.nav.amt.aktivitetskort.kafka.consumer.dto

import no.nav.amt.aktivitetskort.domain.Tiltakstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

data class TiltakstypePayload(
    val id: UUID,
    val navn: String,
    val tiltakskode: String,
) {
    fun toModel() = Tiltakstype(
        id = id,
        navn = navn,
        tiltakskode = Tiltakskode.valueOf(tiltakskode),
    )
}
