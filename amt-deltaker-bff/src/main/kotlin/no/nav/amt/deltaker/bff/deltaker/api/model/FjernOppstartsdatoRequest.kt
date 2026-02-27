package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerForslagEllerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class FjernOppstartsdatoRequest(
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    override fun valider(deltaker: Deltaker) {
        require(deltaker.status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART) {
            "Kan ikke fjerne oppstartsdato for deltaker som ikke venter på oppstart"
        }
        require(deltaker.startdato != null) {
            "Kan ikke fjerne oppstartsdato for deltaker som ikke har oppstartsdato"
        }
        validerForslagEllerBegrunnelse(forslagId, begrunnelse)
        validerBegrunnelse(begrunnelse)
    }
}
