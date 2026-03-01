package no.nav.amt.lib.models.person.extensions

import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adressebeskyttelse

fun Adressebeskyttelse.toBeskyttelsesmarkering() = when (this) {
    Adressebeskyttelse.FORTROLIG -> Beskyttelsesmarkering.FORTROLIG
    Adressebeskyttelse.STRENGT_FORTROLIG -> Beskyttelsesmarkering.STRENGT_FORTROLIG
    Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND -> Beskyttelsesmarkering.STRENGT_FORTROLIG_UTLAND
}
