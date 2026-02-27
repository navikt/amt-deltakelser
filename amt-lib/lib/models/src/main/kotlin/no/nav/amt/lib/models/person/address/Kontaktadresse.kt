package no.nav.amt.lib.models.person.address

data class Kontaktadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val postboksadresse: Postboksadresse?,
)
