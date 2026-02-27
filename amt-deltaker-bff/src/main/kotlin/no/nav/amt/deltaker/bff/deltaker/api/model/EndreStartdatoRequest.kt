package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class EndreStartdatoRequest(
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    private val kanEndreStartdato =
        listOf(
            DeltakerStatus.Type.VENTER_PA_OPPSTART,
            DeltakerStatus.Type.DELTAR,
            DeltakerStatus.Type.HAR_SLUTTET,
            DeltakerStatus.Type.FULLFORT,
            DeltakerStatus.Type.AVBRUTT,
        )

    override fun valider(deltaker: Deltaker) {
        validerDeltakerKanEndres(deltaker)
        validerBegrunnelse(begrunnelse)
        require(deltaker.status.type in kanEndreStartdato) {
            "Kan ikke endre startdato for deltaker med status ${deltaker.status.type}"
        }
        require(startdato == null || !startdato.isBefore(deltaker.deltakerliste.startDato)) {
            "Startdato kan ikke være tidligere enn deltakerlistens startdato"
        }
        sluttdato?.let { validerSluttdatoForDeltaker(it, startdato, deltaker) }
        require(deltakerErEndret(deltaker)) {
            "Både startdato og sluttdato kan ikke være lik som før"
        }
    }

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = deltaker.startdato != startdato ||
        deltaker.sluttdato != sluttdato
}
