package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.veileder.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus.Type
import java.time.LocalDate
import java.util.UUID

data class EndreStartdatoRequest(
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
    val begrunnelse: String?,
    val pavirkerPris: Boolean = false,
    override val forslagId: UUID?,
) : EndringMedForslagRequest {
    override fun valider(deltaker: DeltakerModel) {
        validerDeltakerKanEndres(this, deltaker)
        validerBegrunnelse(begrunnelse)
        val erEnkeltplass = deltaker.gjennomforing.erEnkeltplass
        require(kanEndreStartdato(deltaker.status.type, erEnkeltplass)) {
            "Kan ikke endre startdato for deltaker med status ${deltaker.status.type}"
        }

        require(deltakerErEndret(deltaker)) {
            "Både startdato og sluttdato kan ikke være lik som før"
        }

        sluttdato?.let { validerSluttdatoForDeltaker(it, startdato, deltaker) }

        // For enkeltplasser gir det ikke mening å validere mot gjennomførings-objektet, for dette har
        // ikke start-/sluttdato. For enkeltplasser er startdato/sluttdato lagret på deltaker-objektet.
        if (erEnkeltplass) return

        require(startdato == null || !startdato.isBefore(deltaker.gjennomforing.startDato)) {
            "Startdato kan ikke være tidligere enn deltakerlistens startdato"
        }
    }

    private fun deltakerErEndret(deltaker: DeltakerModel): Boolean = deltaker.startdato != startdato ||
        deltaker.sluttdato != sluttdato

    companion object {
        private fun kanEndreStartdato(
            deltakerStatusType: Type,
            erEnkeltplass: Boolean,
        ): Boolean = deltakerStatusType in setOf(
            Type.VENTER_PA_OPPSTART,
            Type.DELTAR,
            Type.HAR_SLUTTET,
            Type.FULLFORT,
            Type.AVBRUTT,
        ) || (erEnkeltplass && deltakerStatusType == Type.SOKT_INN)
    }
}
