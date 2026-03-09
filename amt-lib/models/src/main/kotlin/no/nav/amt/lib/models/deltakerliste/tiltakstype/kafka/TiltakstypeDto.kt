package no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka

import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltaker.toV1
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.util.UUID

data class TiltakstypeDto(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
    val innsatsgrupper: Set<InnsatsgruppeV2>,
    val deltakerRegistreringInnhold: DeltakerRegistreringInnhold?,
) {
    fun toModel(): Tiltakstype = Tiltakstype(
        id = id,
        navn = navn,
        tiltakskode = tiltakskode,
        innsatsgrupper = innsatsgrupper.map { it.toV1() }.toSet(),
        innhold = deltakerRegistreringInnhold,
    )
}
