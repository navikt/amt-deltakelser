package no.nav.amt.lib.models.deltaker

import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.util.UUID

data class Personalia(
    val personId: UUID?,
    val personident: String,
    val navn: Navn,
    val kontaktinformasjon: Kontaktinformasjon,
    val skjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class Kontaktinformasjon(
    val telefonnummer: String?,
    val epost: String?,
)