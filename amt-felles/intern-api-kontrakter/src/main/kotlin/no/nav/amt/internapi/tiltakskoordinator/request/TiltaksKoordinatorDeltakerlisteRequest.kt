package no.nav.amt.internapi.tiltakskoordinator.request

import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

/**
 * Request for å hente deltakere til tiltakskoordinator-visningen for en gjennomføring.
 *
 * Støtter filtrering på deltakerstatus og om deltakeren har aktive forslag
 * fra arrangør.
 *
 * @property gjennomforingId ID til gjennomføringen deltakerne tilhører.
 * @property handlingFilterValg Valg for filtrering på aktive forslag fra arrangør.
 * @property statuser Filtrerer på aktive deltakerstatuser. Tomt sett betyr ingen statusfiltrering.
 */
data class TiltaksKoordinatorDeltakerlisteRequest(
    val gjennomforingId: UUID = UUID.randomUUID(),
    val handlingFilterValg: Set<HandlingFilterValg> = emptySet(),
    val statuser: Set<DeltakerStatus.Type> = emptySet(),
)
