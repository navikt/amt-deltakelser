package no.nav.amt.deltaker.deltaker.api.deltaker

import no.nav.amt.lib.models.deltaker.DeltakerStatus

val AKTIVE_STATUSER = setOf(
    DeltakerStatus.Type.KLADD,
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
)
val HISTORISKE_STATUSER = setOf(
    DeltakerStatus.Type.AVBRUTT_UTKAST,
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.IKKE_AKTUELL,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.FEILREGISTRERT,
)
