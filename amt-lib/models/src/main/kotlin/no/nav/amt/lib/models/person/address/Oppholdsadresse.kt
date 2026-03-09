package no.nav.amt.lib.models.person.address

data class Oppholdsadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
)
