package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.util.UUID

data class FjernOppstartsdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val begrunnelse: String?,
) : EndringForslagRequest {
    override fun toEndring() = DeltakerEndring.Endring.FjernOppstartsdato(
        begrunnelse = this.begrunnelse,
    )
}
