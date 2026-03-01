package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring

data class InnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val deltakelsesinnhold: Deltakelsesinnhold,
) : EndringRequest {
    override fun toEndring() = DeltakerEndring.Endring.EndreInnhold(
        ledetekst = deltakelsesinnhold.ledetekst,
        innhold = deltakelsesinnhold.innhold,
    )
}
