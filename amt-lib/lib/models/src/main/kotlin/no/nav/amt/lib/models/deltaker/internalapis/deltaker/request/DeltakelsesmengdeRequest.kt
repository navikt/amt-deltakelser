package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

data class DeltakelsesmengdeRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
    val gyldigFra: LocalDate?,
) : EndringForslagRequest {
    override fun toEndring() = DeltakerEndring.Endring.EndreDeltakelsesmengde(
        deltakelsesprosent = this.deltakelsesprosent?.toFloat(),
        dagerPerUke = this.dagerPerUke?.toFloat(),
        begrunnelse = this.begrunnelse,
        gyldigFra = this.gyldigFra,
    )
}
