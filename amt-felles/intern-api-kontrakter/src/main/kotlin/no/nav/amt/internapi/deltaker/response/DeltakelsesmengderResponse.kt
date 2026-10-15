package no.nav.amt.internapi.deltaker.response

data class DeltakelsesmengderResponse(
    // Brukes for å vise fram (kun) den umiddelbart neste mengde som vil tre i kraft
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    // Brukes bare for validering i endredeltakelsesmodal i frontend
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
)
