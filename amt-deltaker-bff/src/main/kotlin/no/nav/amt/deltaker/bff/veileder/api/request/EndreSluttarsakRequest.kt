package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.veileder.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class EndreSluttarsakRequest(
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringMedForslagRequest {
    private val kanEndreSluttarsak = listOf(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.IKKE_AKTUELL, DeltakerStatus.Type.AVBRUTT)

    override fun valider(deltaker: Deltaker) {
        validerAarsaksBeskrivelse(aarsak.beskrivelse)
        require(deltaker.status.type in kanEndreSluttarsak) {
            "Kan ikke endre sluttårsak for deltaker som ikke har sluttet eller er ikke aktuell"
        }
        validerDeltakerKanEndres(deltaker)
        validerBegrunnelse(begrunnelse)
        require(harEndretSluttaarsak(deltaker.status.aarsak, aarsak)) {
            "Sluttårsak må være noe annet enn før"
        }
    }

    override fun valider(deltaker: DeltakerModel) {
        validerAarsaksBeskrivelse(aarsak.beskrivelse)
        require(deltaker.status.type in kanEndreSluttarsak) {
            "Kan ikke endre sluttårsak for deltaker som ikke har sluttet eller er ikke aktuell"
        }
        validerDeltakerKanEndres(deltaker)
        validerBegrunnelse(begrunnelse)
        require(harEndretSluttaarsak(deltaker.status.aarsak, aarsak)) {
            "Sluttårsak må være noe annet enn før"
        }
    }
}
