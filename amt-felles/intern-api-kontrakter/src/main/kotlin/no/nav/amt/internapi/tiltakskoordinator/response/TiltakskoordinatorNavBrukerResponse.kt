package no.nav.amt.internapi.tiltakskoordinator.response

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.extensions.toBeskyttelsesmarkering

data class TiltakskoordinatorNavBrukerResponse(
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val erSkjermet: Boolean,
    val adressebeskyttelse: Adressebeskyttelse?,
    val navEnhet: String?,
    val ikkeDigitalOgManglerAdresse: Boolean,
) {
    @get:JsonIgnore
    val beskyttelsesmarkeringer: List<Beskyttelsesmarkering>
        get() = listOfNotNull(
            adressebeskyttelse?.toBeskyttelsesmarkering(),
            if (erSkjermet) Beskyttelsesmarkering.SKJERMET else null,
        )

    fun getVisningsnavn(tilgangTilBruker: Boolean): Triple<String, String?, String> = when {
        adressebeskyttelse != null && !tilgangTilBruker -> Triple("Adressebeskyttet", null, "")
        erSkjermet && !tilgangTilBruker -> Triple("Skjermet person", null, "")
        else -> Triple(fornavn, mellomnavn, etternavn)
    }
}
