package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesProsent
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakelsesinnhold
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class PameldingUtenGodkjenningRequest(
    val innhold: List<InnholdRequest>,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
) {
    private val kanMeldePaDirekteStatuser = listOf(DeltakerStatus.Type.KLADD, DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

    fun valider(deltaker: Deltaker) {
        require(deltaker.status.type in kanMeldePaDirekteStatuser) {
            "Kan ikke melde på direkte for deltaker med status ${deltaker.status.type}"
        }
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke)
        validerDeltakelsesinnhold(innhold, deltaker.deltakerliste.tiltak.innhold, deltaker.deltakerliste.tiltak.tiltakskode)
    }
}
