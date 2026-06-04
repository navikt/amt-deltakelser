package tjenester.intern

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import http.respondJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tjenester.auth.MOCK_OAUTH2_PORT
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val LOCAL_BFF_PROXY_PORT = 9100
const val LOCAL_BFF_PROXY_PATH_PREFIX = "/amt-deltaker-bff"
const val LOCAL_BFF_SOURCE_HEADER = "x-local-app-source"
private const val LOCAL_BFF_TARGET_BASE_URL = "http://localhost:8080"

private val localBffHttpClient: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
private val localBffObjectMapper = jacksonObjectMapper()



fun Application.localAmtDeltakerBffProxyModule() {
    routing {
        localAmtDeltakerBffProxyRoutes()
    }
}

fun Route.localAmtDeltakerBffProxyRoutes() {
    route(LOCAL_BFF_PROXY_PATH_PREFIX) {
        handle { proxyBffRequest(call) }
        route("{...}") {
            handle { proxyBffRequest(call) }
        }
    }
}

private suspend fun proxyBffRequest(call: ApplicationCall) {
    val requestSource = call.request.header(LOCAL_BFF_SOURCE_HEADER)?.takeIf { it.isNotBlank() } ?: "unknown"
    val issuer = if (requestSource == "innbyggers-flate") "tokenx" else "azure"

    val targetUri = buildTargetUri(call.request.uri)
    val accessToken = withContext(Dispatchers.IO) { fetchLocalDevJwt(requestSource, issuer) }

    if (accessToken == null) {
        respondJson(
            call = call,
            status = HttpStatusCode.BadGateway,
            body = """{"error":"failed to fetch local dev token"}""",
        )
        return
    }

    val requestBody = if (call.request.httpMethod in setOf(HttpMethod.Get, HttpMethod.Head)) {
        null
    } else {
        call.receiveText()
    }

    val proxiedRequest = HttpRequest.newBuilder(targetUri)
        .method(
            call.request.httpMethod.value,
            requestBody?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
        )
        .header(HttpHeaders.Authorization, "Bearer $accessToken")
        .header(LOCAL_BFF_SOURCE_HEADER, requestSource)
        .apply {
            call.request.headers.names().forEach { headerName ->
                if (headerName.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    headerName.equals(HttpHeaders.Host, ignoreCase = true) ||
                    headerName.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
                    headerName.equals(HttpHeaders.Connection, ignoreCase = true)
                ) {
                    return@forEach
                }
                call.request.headers.getAll(headerName).orEmpty().forEach { headerValue ->
                    header(headerName, headerValue)
                }
            }
        }
        .build()

    val proxiedResponse = withContext(Dispatchers.IO) {
        localBffHttpClient.send(proxiedRequest, HttpResponse.BodyHandlers.ofByteArray())
    }

    proxiedResponse.headers().map().forEach { (headerName, headerValues) ->
        if (headerName.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
            headerName.equals(HttpHeaders.TransferEncoding, ignoreCase = true) ||
            headerName.equals(HttpHeaders.Connection, ignoreCase = true)
        ) {
            return@forEach
        }
        headerValues.forEach { headerValue ->
            call.response.headers.append(headerName, headerValue)
        }
    }

    val contentType = proxiedResponse.headers().firstValue(HttpHeaders.ContentType)
        .map { runCatching { ContentType.parse(it) }.getOrNull() }
        .orElse(null)

    call.respondBytes(
        bytes = proxiedResponse.body(),
        contentType = contentType ?: ContentType.Application.Json,
        status = HttpStatusCode.fromValue(proxiedResponse.statusCode()),
    )
}

private fun buildTargetUri(requestUri: String): URI {
    val forwardedPath = requestUri.removePrefix(LOCAL_BFF_PROXY_PATH_PREFIX).ifBlank { "/" }
    val normalizedPath = if (forwardedPath.startsWith('/')) forwardedPath else "/$forwardedPath"
    return URI.create("$LOCAL_BFF_TARGET_BASE_URL$normalizedPath")
}


private fun fetchLocalDevJwt(clientId: String, issuer: String): String? {
    val formBody = listOf(
        "grant_type" to "client_credentials",
        "client_id" to clientId,
        "client_secret" to "frontend-secret",
        "aud" to "amt-deltaker-bff",
    ).joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }

    val tokenRequest = HttpRequest.newBuilder(URI.create("http://localhost:$MOCK_OAUTH2_PORT/$issuer/token"))
        .header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        .POST(HttpRequest.BodyPublishers.ofString(formBody))
        .build()

    val tokenResponse = localBffHttpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString())

    if (tokenResponse.statusCode() !in 200..299) {
        return null
    }

    return runCatching {
        localBffObjectMapper.readTree(tokenResponse.body()).path("access_token").asText(null)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

