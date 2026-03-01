package no.nav.amt.lib.models.person.dto

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.util.UUID

data class NavBrukerDto(
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navVeilederId: UUID?,
    val navEnhet: NavEnhetDto?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode> = emptyList(),
    val innsatsgruppe: Innsatsgruppe? = null,
) {
    fun toModel(): NavBruker = NavBruker(
        personId = personId,
        personident = personident,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        navVeilederId = navVeilederId,
        navEnhetId = navEnhet?.id,
        telefon = telefon,
        epost = epost,
        erSkjermet = erSkjermet,
        adresse = adresse,
        adressebeskyttelse = adressebeskyttelse,
        oppfolgingsperioder = oppfolgingsperioder,
        innsatsgruppe = innsatsgruppe,
    )
}
