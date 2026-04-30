package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    type = DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse = beskrivelse,
)
