package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBakgrunnsinformasjon
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDagerPerUke
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesProsent
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakelsesinnhold
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class PameldingUtenGodkjenningRequest(
    val innhold: List<InnholdsElementRequest>,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
) {
    fun valider(deltaker: DeltakerModel) {
        require(deltaker.status.type in kanMeldePaDirekteStatuser) {
            "Kan ikke melde på direkte for deltaker med status ${deltaker.status.type}"
        }
        validerBakgrunnsinformasjon(bakgrunnsinformasjon)
        validerDeltakelsesProsent(deltakelsesprosent)
        validerDagerPerUke(dagerPerUke)
        validerDeltakelsesinnhold(
            innhold,
            deltaker.gjennomforing.tiltak.innhold,
            deltaker.gjennomforing.tiltak.tiltakskode,
        )
    }

    companion object {
        private val kanMeldePaDirekteStatuser = listOf(
            DeltakerStatus.Type.KLADD,
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        )
    }
}
