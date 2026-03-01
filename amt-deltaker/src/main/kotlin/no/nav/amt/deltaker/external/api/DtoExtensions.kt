package no.nav.amt.deltaker.external.api

import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerStatus.erAktiv() = this.type in listOf(
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
)
