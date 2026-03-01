package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

data class AvbrytDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest {
    override fun toEndring() = DeltakerEndring.Endring.AvbrytDeltakelse(
        aarsak = aarsak,
        sluttdato = sluttdato,
        begrunnelse = begrunnelse,
    )
}
