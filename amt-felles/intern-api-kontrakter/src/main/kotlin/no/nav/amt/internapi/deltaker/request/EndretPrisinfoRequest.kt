package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

data class EndretPrisinfoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val prisinfo: PrisinformasjonDto,
) : EndringRequest {
    override fun toEndring(): DeltakerEndring.Endring = DeltakerEndring.Endring.EndrePrisinfo(
        prisinfo = prisinfo,
    )
}
