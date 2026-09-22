package no.nav.amt.deltaker.bff.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus

val AVSLUTTENDE_STATUSER = setOf(
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.IKKE_AKTUELL,
    DeltakerStatus.Type.FEILREGISTRERT,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.AVBRUTT_UTKAST,
)

/**
 * Statuser der begrenset redigering er tillatt for en låst, nylig avsluttet deltakelse.
 * Speiler frontend sin STATUSER_SOM_TILLATER_BEGRENSET_REDIGERING.
 */
val STATUSER_SOM_TILLATER_BEGRENSET_REDIGERING = setOf(
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.IKKE_AKTUELL,
)
