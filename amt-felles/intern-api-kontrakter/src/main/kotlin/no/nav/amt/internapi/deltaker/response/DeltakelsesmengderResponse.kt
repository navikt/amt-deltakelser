package no.nav.amt.internapi.deltaker.response

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    // Brukes bare for validering i endredeltakelsesmodal i frontend
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
)
