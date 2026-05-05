package no.nav.amt.deltaker.bff.navtiltakskoordinator

import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
import java.util.UUID

data class TiltakskoordinatorsDeltakerlistePayload(
    val id: UUID,
    val navIdent: String,
    val gjennomforingId: UUID,
) {
    companion object {
        fun fromModel(
            model: TiltakskoordinatorDeltakerlisteTilgang,
            navIdent: String,
        ) = TiltakskoordinatorsDeltakerlistePayload(
            id = model.id,
            navIdent = navIdent,
            gjennomforingId = model.deltakerlisteId,
        )
    }
}
