package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

data class EndretPrisinfoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val prisinfo: PrisinformasjonDto,
    val begrunnelse: String?, // påkrevd i frontend, men følger samme mønster som øvrige endringer
) : EndringRequest {
    override fun toEndring(): DeltakerEndring.Endring = DeltakerEndring.Endring.EndrePrisinfo(
        prisinfo = prisinfo,
        begrunnelse = begrunnelse,
    )
}
