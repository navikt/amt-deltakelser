package no.nav.amt.deltaker.bff.auth

import java.util.UUID

data class TiltakskoordinatorsDeltakerlisteDto(
    val id: UUID,
    val navIdent: String,
    val gjennomforingId: UUID,
) {
    companion object {
        fun fromModel(model: TiltakskoordinatorDeltakerlisteTilgang, navIdent: String) = TiltakskoordinatorsDeltakerlisteDto(
            id = model.id,
            navIdent = navIdent,
            gjennomforingId = model.deltakerlisteId,
        )
    }
}
