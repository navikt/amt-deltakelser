package no.nav.amt.deltaker.auth

import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.NavAnsattTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.TilgangType
import java.util.UUID

class TilgangskontrollService(
    private val poaoTilgangCachedClient: PoaoTilgangCachedClient,
) {
    fun verifiserLesetilgang(navAnsattAzureId: UUID, norskIdent: String) {
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
}
