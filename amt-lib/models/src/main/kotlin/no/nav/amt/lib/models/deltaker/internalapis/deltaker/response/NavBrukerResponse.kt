package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse

data class NavBrukerResponse(
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: Innsatsgruppe?,
    val navVeileder: String?,
    val navEnhet: String?,
    val erDigital: Boolean,
)
