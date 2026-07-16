package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

data class EndretPrisinfoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val prisinfo: PrisinformasjonDto,
) : EndringRequest {
    override fun toEndring(): DeltakerEndring.Endring = error(
        "${this::class.simpleName} må kalles via toEndring(tiltak) for å hente ledetekst fra tiltakstypen",
    )

    override fun toEndring(tiltak: Tiltakstype) = DeltakerEndring.Endring.EndrePrisinfo(
        prisinfo = prisinfo,
    )
}
