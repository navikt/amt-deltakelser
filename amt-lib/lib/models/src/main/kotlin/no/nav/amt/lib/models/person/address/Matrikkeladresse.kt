package no.nav.amt.lib.models.person.address

data class Matrikkeladresse(
    val tilleggsnavn: String?,
    val postnummer: String,
    val poststed: String,
)
