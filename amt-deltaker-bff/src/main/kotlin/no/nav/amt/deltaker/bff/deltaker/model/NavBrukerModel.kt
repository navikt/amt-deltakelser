package no.nav.amt.deltaker.bff.deltaker.model

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.extensions.toBeskyttelsesmarkering

data class NavBrukerModel(
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navVeileder: String?,
    val navEnhet: String?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: Innsatsgruppe?,
    val erDigital: Boolean,
) {
    val harAktivOppfolgingsperiode: Boolean
        get() = oppfolgingsperioder.any { it.erAktiv() }

    val beskyttelsesmarkeringer: List<Beskyttelsesmarkering>
        get(): List<Beskyttelsesmarkering> = listOfNotNull(
            adressebeskyttelse?.toBeskyttelsesmarkering(),
            if (erSkjermet) Beskyttelsesmarkering.SKJERMET else null,
        )
}
