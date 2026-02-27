package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.deltaker.deltaker.model.HAR_IKKE_STARTET_STATUSER
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerStatus.harIkkeStartet(): Boolean = type in HAR_IKKE_STARTET_STATUSER

fun DeltakerStatus.Aarsak.getVisningsnavn(): String {
    val beskrivelse = this.beskrivelse
    if (beskrivelse != null) {
        return beskrivelse
    }
    return when (type) {
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
}

fun DeltakerStatus.Type.getStatustekst(): String = when (this) {
    DeltakerStatus.Type.KLADD -> "Kladden er ikke delt"
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> "Utkastet er delt og venter på godkjenning"
    DeltakerStatus.Type.AVBRUTT_UTKAST -> "Avbrutt utkast"
    DeltakerStatus.Type.VENTER_PA_OPPSTART -> "Venter på oppstart"
    DeltakerStatus.Type.DELTAR -> "Deltar"
    DeltakerStatus.Type.HAR_SLUTTET -> "Har sluttet"
    DeltakerStatus.Type.IKKE_AKTUELL -> "Ikke aktuell"
    DeltakerStatus.Type.SOKT_INN -> "Søkt inn"
    DeltakerStatus.Type.VURDERES -> "Vurderes"
    DeltakerStatus.Type.VENTELISTE -> "Venteliste"
    DeltakerStatus.Type.AVBRUTT -> "Avbrutt"
    DeltakerStatus.Type.FULLFORT -> "Fullført"
    DeltakerStatus.Type.FEILREGISTRERT -> "Feilregistrert"
    DeltakerStatus.Type.PABEGYNT_REGISTRERING -> "Påbegynt registrering"
}
