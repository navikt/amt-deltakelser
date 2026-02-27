package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesProsent
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesmengde
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class EndreDeltakelsesmengdeRequest(
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
    val gyldigFra: LocalDate = LocalDate.now(),
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    override fun valider(deltaker: Deltaker) {
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke)

        deltaker.sluttdato?.let {
            require(!gyldigFra.isAfter(it)) {
                "Deltakelsesmengde kan ikke endres etter deltaker sin sluttdato"
            }
        }

        if (deltaker.status.type != DeltakerStatus.Type.VENTER_PA_OPPSTART && deltaker.startdato != null) {
            require(!gyldigFra.isBefore(deltaker.startdato)) {
                "Deltakelsesmengde kan ikke endres før deltaker sin startdato"
            }
        }

        validerDeltakelsesmengde(deltakelsesprosent, dagerPerUke, gyldigFra, deltaker)

        validerDeltakerKanEndres(deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
