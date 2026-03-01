package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.deltaker.api.utils.statusForMindreEnn15DagerSiden
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class IkkeAktuellRequest(
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    private val kanBliIkkeAktuell = listOf(
        DeltakerStatus.Type.VENTER_PA_OPPSTART,
        DeltakerStatus.Type.DELTAR,
        DeltakerStatus.Type.IKKE_AKTUELL,
        DeltakerStatus.Type.VENTELISTE,
        DeltakerStatus.Type.VURDERES,
        DeltakerStatus.Type.SOKT_INN,
    )

    override fun valider(deltaker: Deltaker) {
        validerAarsaksBeskrivelse(aarsak.beskrivelse)
        require(deltaker.status.type in kanBliIkkeAktuell) {
            "Kan ikke sette deltaker med status ${deltaker.status.type} til ikke aktuell"
        }
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR) {
            require(statusForMindreEnn15DagerSiden(deltaker)) {
                "Deltaker med deltar-status mer enn 15 dager tilbake i tid kan ikke settes til ikke aktuell"
            }
            require(forslagId != null) {
                "Kan bare sette deltaker som deltar til ikke aktuell hvis det foreligger et forslag"
            }
        }
        validerDeltakerKanEndres(deltaker)
        validerBegrunnelse(begrunnelse)
        require(deltakerErEndret(deltaker)) {
            "Kan ikke oppdatere deltaker som allerede er ikke aktuell med samme årsak"
        }
    }

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = deltaker.status.type != DeltakerStatus.Type.IKKE_AKTUELL ||
        harEndretSluttaarsak(deltaker.status.aarsak, aarsak)
}
