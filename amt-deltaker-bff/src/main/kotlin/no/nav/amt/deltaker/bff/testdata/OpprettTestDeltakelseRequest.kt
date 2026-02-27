package no.nav.amt.deltaker.bff.testdata

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesProsent
import java.time.LocalDate
import java.util.UUID

data class OpprettTestDeltakelseRequest(
    val personident: String,
    val deltakerlisteId: UUID,
    val startdato: LocalDate,
    val deltakelsesprosent: Int,
    val dagerPerUke: Int?,
) {
    fun valider() {
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke)
    }
}
