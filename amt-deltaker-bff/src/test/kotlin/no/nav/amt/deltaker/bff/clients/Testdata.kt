package no.nav.amt.deltaker.bff.clients

import no.nav.amt.deltaker.bff.testdata.OpprettTestDeltakelseRequest
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.internapi.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold

const val TESTVEILEDER = "Z990098"
const val TESTENHET = "0314"

object Testdata {
    fun lagUtkast(opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest) = UtkastRequest(
        deltakelsesinnhold = lagInnhold(),
        bakgrunnsinformasjon = null,
        deltakelsesprosent = opprettTestDeltakelseRequest.deltakelsesprosent.toFloat(),
        dagerPerUke = opprettTestDeltakelseRequest.dagerPerUke?.toFloat(),
        endretAv = TESTVEILEDER,
        endretAvEnhet = TESTENHET,
        godkjentAvNav = true,
    )

    private fun lagInnhold(): Deltakelsesinnhold {
        val innhold = lagDeltakerRegistreringInnhold()
        val valgtInnhold = innhold.innholdselementer.firstOrNull()?.toInnhold(valgt = true)
        return Deltakelsesinnhold(
            ledetekst = innhold.ledetekst,
            innhold = valgtInnhold?.let { listOf(it) } ?: emptyList(),
        )
    }
}
