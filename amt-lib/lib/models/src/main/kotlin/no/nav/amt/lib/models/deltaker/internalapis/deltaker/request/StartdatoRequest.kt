package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

data class StartdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
    val begrunnelse: String?,
) : EndringForslagRequest {
    override fun toEndring() = DeltakerEndring.Endring.EndreStartdato(
        startdato = startdato,
        sluttdato = sluttdato,
        begrunnelse = begrunnelse,
    )
}
