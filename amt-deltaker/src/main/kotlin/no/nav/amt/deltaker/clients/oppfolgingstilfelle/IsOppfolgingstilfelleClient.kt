package no.nav.amt.deltaker.clients.oppfolgingstilfelle

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.time.LocalDate

class IsOppfolgingstilfelleClient(
    private val baseUrl: String,
    private val scope: String,
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
) {
    companion object {
        private const val PERSONIDENT_HEADER = "nav-personident"
    }

    suspend fun erSykmeldtMedArbeidsgiver(personident: String): Boolean = httpClient
        .get("$baseUrl/api/system/v1/oppfolgingstilfelle/personident") {
            header(HttpHeaders.Authorization, azureAdTokenClient.getMachineToMachineToken(scope))
            header(PERSONIDENT_HEADER, personident)
            accept(ContentType.Application.Json)
        }.failIfNotSuccess("Kunne ikke hente oppfølgingstilfelle fra isoppfolgingstilfelle.")
        .body<OppfolgingstilfellePersonResponse>()
        .oppfolgingstilfelleList
        .filter { it.gyldigForDato(LocalDate.now()) }
        .any { it.arbeidstakerAtTilfelleEnd }
}
