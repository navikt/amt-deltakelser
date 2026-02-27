package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.util.UUID

data class SluttarsakRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest {
    override fun toEndring() = DeltakerEndring.Endring.EndreSluttarsak(
        aarsak = aarsak,
        begrunnelse = begrunnelse,
    )
}
