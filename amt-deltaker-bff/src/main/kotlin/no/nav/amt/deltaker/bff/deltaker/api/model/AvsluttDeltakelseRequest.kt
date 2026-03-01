package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class AvsluttDeltakelseRequest(
    val aarsak: DeltakerEndring.Aarsak?,
    val sluttdato: LocalDate?,
    val harDeltatt: Boolean? = true,
    val harFullfort: Boolean? = null,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    fun harDeltatt(): Boolean = harDeltatt ?: true

    fun harFullfort(): Boolean = harFullfort ?: true

    override fun valider(deltaker: Deltaker) {
        validerAarsaksBeskrivelse(aarsak?.beskrivelse)
        validerBegrunnelse(begrunnelse)
        validerDeltakerKanEndres(deltaker)

        require(deltaker.status.type in kanAvslutteDeltakelse) { statusTypeErrorText }

        if (harDeltatt()) {
            require(sluttdato != null) {
                "Må angi sluttdato for deltaker som har deltatt"
            }
        }
        sluttdato?.let { validerSluttdatoForDeltaker(it, deltaker.startdato, deltaker) }

        require(deltakerErEndret(deltaker)) {
            "Kan ikke avslutte deltakelse med uendret sluttdato og årsak"
        }
    }

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
        deltaker.sluttdato != sluttdato ||
        harEndretSluttaarsak(deltaker.status.aarsak, aarsak)

    companion object {
        private val kanAvslutteDeltakelse =
            setOf(DeltakerStatus.Type.DELTAR, DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT)

        private val statusTypeErrorText =
            "Avslutte deltakelse for deltaker krever en av følgende statuser: ${kanAvslutteDeltakelse.joinToString(", ")}"
    }
}
