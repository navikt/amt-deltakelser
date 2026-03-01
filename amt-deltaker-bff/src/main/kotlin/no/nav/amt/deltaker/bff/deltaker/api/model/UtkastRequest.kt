package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesProsent
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class UtkastRequest(
    val innhold: List<InnholdRequest>,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
) {
    private val kanUpserteUtkastStatuser = listOf(DeltakerStatus.Type.KLADD, DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

    fun valider(deltaker: Deltaker, digitalBruker: Boolean) {
        require(digitalBruker) {
            "Kan ikke dele utkast med en bruker som ikke er digital"
        }
        require(deltaker.status.type in kanUpserteUtkastStatuser) {
            "Kan ikke lagre utkast for deltaker med status ${deltaker.status.type}"
        }
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke)
        validerDeltakelsesinnhold(innhold, deltaker.deltakerliste.tiltak.innhold, deltaker.deltakerliste.tiltak.tiltakskode)
    }
}
