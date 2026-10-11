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
    val begrunnelse: String?, // denne er vel påkrevd i frontend og bør kunne være non-nullable
    val gyldigFra: LocalDate = LocalDate.now(), // denne er påkrevd i frontend og default bør kunne fjernes
    val pavirkerPris: Boolean = false,
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

        validerDeltakelsesmengde(
            nyProsent = deltakelsesprosent,
            nyDagerPerUke = dagerPerUke,
            gyldigFra = gyldigFra,
            eksisterendeDeltaker = deltaker,
        )

        validerDeltakerKanEndres(
            request = this,
            opprinneligDeltaker = deltaker,
        )

        validerBegrunnelse(begrunnelse)
    }
}
