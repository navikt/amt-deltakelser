package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres

class TilbakekallPrisendringRequest : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        validerDeltakerKanEndres(this, deltaker)
    }
}
