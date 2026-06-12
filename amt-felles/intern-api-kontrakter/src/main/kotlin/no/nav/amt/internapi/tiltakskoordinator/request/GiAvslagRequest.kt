package no.nav.amt.internapi.tiltakskoordinator.request

import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.util.UUID

data class GiAvslagRequest(
    val gjennomforingId: UUID,
    val deltakerId: UUID,
    val avslag: EndringFraTiltakskoordinator.Avslag,
    val endretAv: String,
)
