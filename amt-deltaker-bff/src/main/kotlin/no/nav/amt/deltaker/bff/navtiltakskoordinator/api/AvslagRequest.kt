package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.util.UUID

data class AvslagRequest(
    val deltakerId: UUID,
    val aarsak: EndringFraTiltakskoordinator.Avslag.Aarsak,
    val begrunnelse: String?,
)
