package no.nav.amt.deltaker.external.data

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.util.UUID

data class DeltakerPersonaliaResponse(
    val deltakerId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navEnhetsnummer: String?,
    val erSkjermet: Boolean,
    val adressebeskyttelse: AdressebeskyttelseResponse?,
) {
    enum class AdressebeskyttelseResponse {
        STRENGT_FORTROLIG_UTLAND,
        STRENGT_FORTROLIG,
        FORTROLIG,
        UGRADERT,
    }

    companion object {
        fun from(deltaker: Deltaker, navEnheter: Map<UUID, NavEnhet>): DeltakerPersonaliaResponse {
            val navEnhet = deltaker.navBruker.navEnhetId?.let { navEnheter[it] }

            return DeltakerPersonaliaResponse(
                deltakerId = deltaker.id,
                personident = deltaker.navBruker.personident,
                fornavn = deltaker.navBruker.fornavn,
                mellomnavn = deltaker.navBruker.mellomnavn,
                etternavn = deltaker.navBruker.etternavn,
                navEnhetsnummer = navEnhet?.enhetsnummer,
                erSkjermet = deltaker.navBruker.erSkjermet,
                adressebeskyttelse = when (deltaker.navBruker.adressebeskyttelse) {
                    Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND -> {
                        AdressebeskyttelseResponse.STRENGT_FORTROLIG_UTLAND
                    }

                    Adressebeskyttelse.STRENGT_FORTROLIG -> {
                        AdressebeskyttelseResponse.STRENGT_FORTROLIG
                    }

                    Adressebeskyttelse.FORTROLIG -> {
                        AdressebeskyttelseResponse.FORTROLIG
                    }

                    null -> {
                        null
                    }
                },
            )
        }
    }
}
