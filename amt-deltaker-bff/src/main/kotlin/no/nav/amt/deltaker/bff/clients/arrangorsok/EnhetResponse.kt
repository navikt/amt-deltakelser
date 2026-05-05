package no.nav.amt.deltaker.bff.clients.arrangorsok

data class EnhetResponse(
    val organisasjonsnummer: String,
    val organisasjonsform: String,
    val navn: String,
    val overordnetEnhet: String?,
)
