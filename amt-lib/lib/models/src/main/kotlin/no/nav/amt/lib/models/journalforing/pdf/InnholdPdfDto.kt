package no.nav.amt.lib.models.journalforing.pdf

data class InnholdPdfDto(
    /*
        Tiltak med individuell oppfølging har ofte innholdselementer som nav veileder kan krysse av.
        I visse tilfeller inkluderer disse også elementet Annet som i tillegg har en fritekstbeskrivelse.
    */
    val valgteInnholdselementer: List<String>?,

    /*
        Noen tiltak har kun fritekstbeskrivelse uten forhåndsdefinerte innholdselementer.
     */
    val fritekstBeskrivelse: String?,

    /*
        Tiltak beskrivelse brukes i visse tilfeller som ledetekst for innholdet i hovedvedtaket
     */
    val ledetekst: String?,
)