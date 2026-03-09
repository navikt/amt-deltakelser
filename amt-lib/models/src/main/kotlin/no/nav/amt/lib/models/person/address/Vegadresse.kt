package no.nav.amt.lib.models.person.address

data class Vegadresse(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val tilleggsnavn: String?,
    val postnummer: String,
    val poststed: String,
)
