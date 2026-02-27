package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import java.util.UUID

object EndringTestUtils {
    val mockDeltakelsesmengdeProvider: (UUID) -> Deltakelsesmengder = { _ -> emptyList<DeltakerHistorikk>().toDeltakelsesmengder() }
}
