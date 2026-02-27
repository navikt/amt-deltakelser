package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring

data class BakgrunnsinformasjonRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val bakgrunnsinformasjon: String?,
) : EndringRequest {
    override fun toEndring() = DeltakerEndring.Endring.EndreBakgrunnsinformasjon(bakgrunnsinformasjon)
}
