package no.nav.amt.api.tiltakskoordinator.request

import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.util.UUID

data class GiAvslagRequest(
    val deltakerId: UUID,
    val avslag: EndringFraTiltakskoordinator.Avslag,
    val endretAv: String,
)
