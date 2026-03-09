package no.nav.amt.lib.models.person.address

data class Bostedsadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
)
