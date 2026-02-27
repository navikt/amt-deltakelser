package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class EndreSluttarsakRequest(
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
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
}
