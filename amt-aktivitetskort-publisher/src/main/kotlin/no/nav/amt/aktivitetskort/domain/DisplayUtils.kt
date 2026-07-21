package no.nav.amt.aktivitetskort.domain

import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun displayText(deltakerStatus: DeltakerStatusModel) = if (deltakerStatus.aarsak != null) {
    "${deltakerStatus.type.display()} - årsak: ${deltakerStatus.aarsak.display().lowercase()}"
} else {
    deltakerStatus.type.display()
}

fun DeltakerStatus.Type.display() = when (this) {
    DeltakerStatus.Type.VENTER_PA_OPPSTART -> "Venter på oppstart"
    DeltakerStatus.Type.DELTAR -> "Deltar"
    DeltakerStatus.Type.HAR_SLUTTET -> "Har sluttet"
    DeltakerStatus.Type.IKKE_AKTUELL -> "Ikke aktuell"
    DeltakerStatus.Type.FEILREGISTRERT -> "Feilregistrert"
    DeltakerStatus.Type.SOKT_INN -> "Søkt inn"
    DeltakerStatus.Type.VURDERES -> "Vurderes"
    DeltakerStatus.Type.VENTELISTE -> "Venteliste"
    DeltakerStatus.Type.AVBRUTT -> "Avbrutt"
    DeltakerStatus.Type.FULLFORT -> "Fullført"
    DeltakerStatus.Type.PABEGYNT_REGISTRERING -> "Søkt inn"
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> "Utkast til påmelding"
    DeltakerStatus.Type.AVBRUTT_UTKAST -> "Avbrutt utkast"
    else -> throw IllegalStateException("Unknown type: $this")
}

fun DeltakerStatus.Aarsak.Type.display() = when (this) {
    DeltakerStatus.Aarsak.Type.SYK -> "Syk"
    DeltakerStatus.Aarsak.Type.FATT_JOBB -> "Fått jobb"
    DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE -> "Trenger annen støtte"
    DeltakerStatus.Aarsak.Type.FIKK_IKKE_PLASS -> "Fikk ikke plass"
    DeltakerStatus.Aarsak.Type.IKKE_MOTT -> "Møter ikke opp"
    DeltakerStatus.Aarsak.Type.ANNET -> "Annet"
    DeltakerStatus.Aarsak.Type.AVLYST_KONTRAKT -> "Avlyst kontrakt"
    DeltakerStatus.Aarsak.Type.UTDANNING -> "Utdanning"
    DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT -> "Samarbeidet med arrangøren er avbrutt"
    DeltakerStatus.Aarsak.Type.KRAV_IKKE_OPPFYLT -> "Krav for deltakelse er ikke oppfylt"
    DeltakerStatus.Aarsak.Type.KURS_FULLT -> "Kurset er fullt"
}
