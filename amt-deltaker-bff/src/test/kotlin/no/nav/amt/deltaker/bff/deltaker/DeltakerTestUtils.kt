package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus

object DeltakerTestUtils {
    fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
        type = DeltakerStatus.Aarsak.Type.valueOf(type.name),
        beskrivelse = beskrivelse,
    )
}
