package no.nav.amt.lib.ktor.clients

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient

abstract class ApiClientBase(
    protected val baseUrl: String,
    protected val scope: String,
    protected val httpClient: HttpClient,
    protected val azureAdTokenClient: AzureAdTokenClient,
) {
    protected suspend fun performGet(urlSubPath: String): HttpResponse = httpClient.get("$baseUrl/$urlSubPath") {
        header(HttpHeaders.Authorization, azureAdTokenClient.getMachineToMachineToken(scope))
        accept(ContentType.Application.Json)
    }

    protected suspend fun performPost(urlSubPath: String, requestBody: Any?): HttpResponse = httpClient.post("$baseUrl/$urlSubPath") {
        header(HttpHeaders.Authorization, azureAdTokenClient.getMachineToMachineToken(scope))
        accept(ContentType.Application.Json)
        if (requestBody != null) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    protected suspend fun performDelete(urlSubPath: String): HttpResponse = httpClient.delete("$baseUrl/$urlSubPath") {
        header(HttpHeaders.Authorization, azureAdTokenClient.getMachineToMachineToken(scope))
    }
}
