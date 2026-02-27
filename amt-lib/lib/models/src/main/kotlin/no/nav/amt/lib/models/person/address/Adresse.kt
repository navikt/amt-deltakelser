package no.nav.amt.lib.models.person.address

data class Adresse(
    val bostedsadresse: Bostedsadresse?,
    val oppholdsadresse: Oppholdsadresse?,
    val kontaktadresse: Kontaktadresse?,
)
