package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesProsent
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesmengde
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import java.time.LocalDate
import java.util.UUID

data class EndreDeltakelsesmengdeRequest(
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
    val gyldigFra: LocalDate = LocalDate.now(),
    override val forslagId: UUID?,
) : EndringMedForslagRequest {
    override fun valider(deltaker: DeltakerModel) {
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke, deltaker.gjennomforing.erEnkeltplass)

        deltaker.sluttdato?.let {
            require(!gyldigFra.isAfter(it)) {
                "Deltakelsesmengde kan ikke endres etter deltaker sin sluttdato"
            }
        }

        deltaker.startdato?.let {
            require(!gyldigFra.isBefore(it)) {
                "Deltakelsesmengde kan ikke endres før deltaker sin startdato"
            }
        }

        validerDeltakelsesmengde(deltakelsesprosent, dagerPerUke, gyldigFra, deltaker)

        validerDeltakerKanEndres(this, deltaker)
        validerBegrunnelse(begrunnelse)
    }
}
