package no.nav.amt.deltaker.bff.deltaker.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus

val AVSLUTTENDE_STATUSER = setOf(
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.IKKE_AKTUELL,
    DeltakerStatus.Type.FEILREGISTRERT,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.AVBRUTT_UTKAST,
)

val AKTIVE_STATUSER = setOf(
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VENTELISTE,
)
