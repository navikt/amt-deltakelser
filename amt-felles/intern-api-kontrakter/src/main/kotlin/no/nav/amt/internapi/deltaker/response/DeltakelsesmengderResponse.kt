package no.nav.amt.internapi.deltaker.response

@Deprecated("Skal fjernes", replaceWith = ReplaceWith("Ingen"))
data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    // Brukes bare for validering i endredeltakelsesmodal i frontend
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
)
