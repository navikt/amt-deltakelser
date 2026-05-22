package no.nav.amt.internapi.deltaker.response

data class DeltakelsesmengderResponse(
    // deltakelsesmengde som vil være gjeldende i fremtiden om det finnes en fremtidig deltakelsesmengde
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    // Den nåværende deltakelsesmengden som er gyldig eller?
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
)
