package no.nav.amt.lib.models.person

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.extensions.toBeskyttelsesmarkering
import java.util.UUID

data class NavBruker(
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navVeilederId: UUID?,
    val navEnhetId: UUID?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: Innsatsgruppe?,
) {
    val fulltNavn get() = listOfNotNull(fornavn, mellomnavn, etternavn).joinToString(" ")

    val erAdressebeskyttet get() = adressebeskyttelse != null

    val beskyttelsesmarkeringer: List<Beskyttelsesmarkering>
        get(): List<Beskyttelsesmarkering> = listOfNotNull(
            adressebeskyttelse?.toBeskyttelsesmarkering(),
            if (erSkjermet) Beskyttelsesmarkering.SKJERMET else null,
        )

    val harAktivOppfolgingsperiode: Boolean
        get() = oppfolgingsperioder.any { it.erAktiv() }

    fun getVisningsnavn(tilgangTilBruker: Boolean): Triple<String, String?, String> = when {
        erAdressebeskyttet && !tilgangTilBruker -> Triple(ADRESSEBESKYTTET_PLACEHOLDER_NAVN, null, "")
        erSkjermet && !tilgangTilBruker -> Triple(SKJERMET_PERSON_PLACEHOLDER_NAVN, null, "")
        else -> Triple(fornavn, mellomnavn, etternavn)
    }

    companion object {
        internal const val ADRESSEBESKYTTET_PLACEHOLDER_NAVN = "Adressebeskyttet"
        internal const val SKJERMET_PERSON_PLACEHOLDER_NAVN = "Skjermet person"
    }
}
