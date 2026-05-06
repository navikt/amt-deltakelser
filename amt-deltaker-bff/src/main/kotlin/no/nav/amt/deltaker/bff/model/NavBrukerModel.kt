package no.nav.amt.deltaker.bff.model

import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils.ADRESSEBESKYTTET_PLACEHOLDER_NAVN
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils.SKJERMET_PERSON_PLACEHOLDER_NAVN
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
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
    val navVeileder: NavVeilederResponse?,
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
    val erAdressebeskyttet get() = adressebeskyttelse != null

    fun getVisningsnavn(tilgangTilBruker: Boolean): Triple<String, String?, String> = when {
        erAdressebeskyttet && !tilgangTilBruker -> Triple(ADRESSEBESKYTTET_PLACEHOLDER_NAVN, null, "")
        erSkjermet && !tilgangTilBruker -> Triple(SKJERMET_PERSON_PLACEHOLDER_NAVN, null, "")
        else -> Triple(fornavn, mellomnavn, etternavn)
    }

    val harAktivOppfolgingsperiode: Boolean
        get() = oppfolgingsperioder.any { it.erAktiv() }

    val beskyttelsesmarkeringer: List<Beskyttelsesmarkering>
        get(): List<Beskyttelsesmarkering> = listOfNotNull(
            adressebeskyttelse?.toBeskyttelsesmarkering(),
            if (erSkjermet) Beskyttelsesmarkering.SKJERMET else null,
        )
}
