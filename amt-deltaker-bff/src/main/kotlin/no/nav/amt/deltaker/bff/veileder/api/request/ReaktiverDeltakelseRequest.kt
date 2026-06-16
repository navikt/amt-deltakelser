package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerAktivGjennomforing
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanReaktiveres

data class ReaktiverDeltakelseRequest(
    val begrunnelse: String,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        validerDeltakerKanReaktiveres(deltaker)
        validerBegrunnelse(begrunnelse)
        validerAktivGjennomforing(deltaker.gjennomforing)
        validerDeltakerKanEndres(this, deltaker)
    }
}
