package no.nav.amt.distribusjon.hendelse.model

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.hendelse.InnholdDto

fun InnholdDto.visningsnavn(): String = beskrivelse ?: tekst

fun DeltakerEndring.Aarsak.visningsnavn(): String = beskrivelse ?: when (type) {
    DeltakerEndring.Aarsak.Type.SYK -> "Syk"
    DeltakerEndring.Aarsak.Type.FATT_JOBB -> "Fått jobb"
    DeltakerEndring.Aarsak.Type.TRENGER_ANNEN_STOTTE -> "Trenger annen støtte"
    DeltakerEndring.Aarsak.Type.UTDANNING -> "Utdanning"
    DeltakerEndring.Aarsak.Type.IKKE_MOTT -> "Møter ikke opp"
    DeltakerEndring.Aarsak.Type.ANNET -> "Annet"
}
