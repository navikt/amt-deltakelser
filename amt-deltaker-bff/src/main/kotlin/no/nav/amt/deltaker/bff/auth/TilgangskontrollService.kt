package no.nav.amt.deltaker.bff.auth

import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.EksternBrukerTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleSkjermedePersonerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleStrengtFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.TilgangType
import java.util.UUID

class TilgangskontrollService(
    private val poaoTilgangCachedClient: PoaoTilgangCachedClient,
) {
    fun verifiserSkrivetilgang(
        navAnsattAzureId: UUID,
        norskIdent: String,
    ) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                NavAnsattTilgangTilEksternBrukerPolicyInput(
                    navAnsattAzureId,
                    TilgangType.SKRIVE,
                    norskIdent,
                ),
            ).getOrDefault(Decision.Deny("Ansatt har ikke skrivetilgang til bruker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Ansatt har ikke skrivetilgang til bruker")
        }
    }

    fun verifiserLesetilgang(
        navAnsattAzureId: UUID,
        norskIdent: String,
    ) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                NavAnsattTilgangTilEksternBrukerPolicyInput(
                    navAnsattAzureId,
                    TilgangType.LESE,
                    norskIdent,
                ),
            ).getOrDefault(Decision.Deny("Ansatt har ikke lesetilgang til bruker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Ansatt har ikke lesetilgang til bruker")
        }
    }

    fun verifiserInnbyggersTilgangTilDeltaker(
        rekvirentPersonident: String,
        ressursPersonident: String,
    ) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                EksternBrukerTilgangTilEksternBrukerPolicyInput(rekvirentPersonident, ressursPersonident),
            ).getOrDefault(Decision.Deny("Innbygger har ikke tilgang til deltaker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Innbygger har ikke tilgang til deltaker")
        }
    }

    fun vurderAdressebeskyttelseTilgang(
        adressebeskyttelse: Adressebeskyttelse?,
        navAnsattAzureId: UUID,
    ): Decision = when (adressebeskyttelse) {
        Adressebeskyttelse.FORTROLIG ->
            poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId)).getOrThrow()

        Adressebeskyttelse.STRENGT_FORTROLIG, Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND ->
            poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId)).getOrThrow()

        else -> Decision.Permit
    }

    fun vurderSkjermingTilgang(
        erSkjermet: Boolean,
        navAnsattAzureId: UUID,
    ): Decision = if (erSkjermet) {
        poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId)).getOrThrow()
    } else {
        Decision.Permit
    }
}
