package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class EndreSluttdatoRequest(
    val sluttdato: LocalDate,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    private val kanEndreSluttdato = listOf(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT)

    override fun valider(deltaker: Deltaker) {
        validerDeltakerKanEndres(deltaker)
        require(deltaker.status.type in kanEndreSluttdato) {
            "Kan ikke endre sluttdato for deltaker som ikke har sluttet"
        }
        require(sluttdato != deltaker.sluttdato) {
            "Sluttdato kan ikke være lik som før"
        }
        validerSluttdatoForDeltaker(sluttdato, deltaker.startdato, deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
