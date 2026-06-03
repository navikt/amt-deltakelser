import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

private const val LOCAL_BFF_PROXY_PATH_PREFIX = "/amt-deltaker-bff"
private const val LOCAL_BFF_TARGET_BASE_URL = "http://localhost:8080"
private const val LOCAL_TOKEN_ENDPOINT = "http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID/token"

private val localBffHttpClient: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
private val localBffObjectMapper = jacksonObjectMapper()

@Volatile
private var cachedLocalDevJwt: String? = null
private val localDevJwtLock = Any()

fun invalidateLocalDevJwtCache() {
    synchronized(localDevJwtLock) {
        cachedLocalDevJwt = null
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
    val targetUri = buildTargetUri(call.request.uri)
    val accessToken = resolveLocalDevJwt()

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

private suspend fun resolveLocalDevJwt(): String? {
    cachedLocalDevJwt?.let { return it }

    val fetchedToken = withContext(Dispatchers.IO) {
        fetchLocalDevJwt()
    } ?: return null

    return synchronized(localDevJwtLock) {
        cachedLocalDevJwt?.let { return@synchronized it }
        cachedLocalDevJwt = fetchedToken
        fetchedToken
    }
}

private fun fetchLocalDevJwt(): String? {
    val formBody = listOf(
        "grant_type" to "client_credentials",
        "client_id" to "frontend-client-id",
        "client_secret" to "frontend-secret",
        "aud" to "amt-deltaker-bff",
    ).joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }

    val tokenRequest = HttpRequest.newBuilder(URI.create(LOCAL_TOKEN_ENDPOINT))
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

