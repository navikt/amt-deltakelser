package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate

data class ReaktiverDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val begrunnelse: String,
) : EndringRequest {
    override fun toEndring() = DeltakerEndring.Endring.ReaktiverDeltakelse(
        reaktivertDato = LocalDate.now(),
        begrunnelse = begrunnelse,
    )
}
