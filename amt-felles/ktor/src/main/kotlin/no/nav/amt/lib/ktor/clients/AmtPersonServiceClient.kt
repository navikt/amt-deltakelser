package no.nav.amt.lib.ktor.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavAnsattRequest
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.models.person.dto.NavBrukerFodselsarDto
import no.nav.amt.lib.models.person.dto.NavBrukerRequest
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.models.person.dto.NavEnhetRequest
import java.util.UUID

class AmtPersonServiceClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun hentNavAnsatt(navIdent: String): NavAnsatt = performPost("api/nav-ansatt", NavAnsattRequest(navIdent))
        .failIfNotSuccess("Kunne ikke hente nav-ansatt med ident $navIdent fra amt-person-service.")
        .body()

    suspend fun hentNavAnsatt(id: UUID): NavAnsatt = performGet("api/nav-ansatt/$id")
        .failIfNotSuccess("Kunne ikke hente nav-ansatt med id $id fra amt-person-service.")
        .body()

    suspend fun hentNavEnhet(navEnhetsnummer: String): NavEnhet = performPost("api/nav-enhet", NavEnhetRequest(navEnhetsnummer))
        .failIfNotSuccess("Kunne ikke hente nav-enhet med nummer $navEnhetsnummer fra amt-person-service.")
        .body<NavEnhetDto>()
        .toModel()

    suspend fun hentNavEnhet(id: UUID): NavEnhet = performGet("api/nav-enhet/$id")
        .failIfNotSuccess("Kunne ikke hente nav-enhet med id $id fra amt-person-service.")
        .body<NavEnhetDto>()
        .toModel()

    suspend fun hentNavBruker(personIdent: String): NavBruker = performPost("api/nav-bruker", NavBrukerRequest(personIdent))
        .failIfNotSuccess("Kunne ikke hente nav-bruker fra amt-person-service")
        .body<NavBrukerDto>()
        .toModel()

    suspend fun hentNavBrukerFodselsar(personIdent: String): Int = performPost("api/nav-bruker-fodselsar", NavBrukerRequest(personIdent))
        .failIfNotSuccess("Kunne ikke hente fodselsar for nav-bruker fra amt-person-service")
        .body<NavBrukerFodselsarDto>()
        .fodselsar
}
