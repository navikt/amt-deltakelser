package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth

import java.time.LocalDateTime
import java.util.UUID

data class TiltakskoordinatorDeltakerlisteTilgang(
    val id: UUID,
    val navAnsattId: UUID,
    val deltakerlisteId: UUID,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
)
