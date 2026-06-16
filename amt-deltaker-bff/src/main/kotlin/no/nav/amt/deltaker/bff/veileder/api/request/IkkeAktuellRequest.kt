package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.veileder.api.utils.statusForMindreEnn15DagerSiden
import no.nav.amt.deltaker.bff.veileder.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class IkkeAktuellRequest(
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringMedForslagRequest {
    private val kanBliIkkeAktuell = listOf(
        DeltakerStatus.Type.VENTER_PA_OPPSTART,
        DeltakerStatus.Type.DELTAR,
        DeltakerStatus.Type.IKKE_AKTUELL,
        DeltakerStatus.Type.VENTELISTE,
        DeltakerStatus.Type.VURDERES,
        DeltakerStatus.Type.SOKT_INN,
    )

    override fun valider(deltaker: DeltakerModel) {
        validerAarsaksBeskrivelse(aarsak.beskrivelse)
        require(deltaker.status.type in kanBliIkkeAktuell) {
            "Kan ikke sette deltaker med status ${deltaker.status.type} til ikke aktuell"
        }
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR) {
            require(statusForMindreEnn15DagerSiden(deltaker.status)) {
                "Deltaker med deltar-status mer enn 15 dager tilbake i tid kan ikke settes til ikke aktuell"
            }
            require(forslagId != null) {
                "Kan bare sette deltaker som deltar til ikke aktuell hvis det foreligger et forslag"
            }
        }
        validerDeltakerKanEndres(this, deltaker)
        validerBegrunnelse(begrunnelse)
        require(deltakerErEndret(deltaker.status)) {
            "Kan ikke oppdatere deltaker som allerede er ikke aktuell med samme årsak"
        }
    }

    private fun deltakerErEndret(deltakerStatus: DeltakerStatus): Boolean = deltakerStatus.type != DeltakerStatus.Type.IKKE_AKTUELL ||
        harEndretSluttaarsak(deltakerStatus.aarsak, aarsak)
}
