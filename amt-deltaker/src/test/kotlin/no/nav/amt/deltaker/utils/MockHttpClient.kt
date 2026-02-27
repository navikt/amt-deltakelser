package no.nav.amt.deltaker.utils

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.OppfolgingstilfellePersonResponse
import no.nav.amt.deltaker.bff.apiclients.arrangor.ArrangorResponse
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory

const val AMT_PERSON_URL = "http://amt-person-service"
const val ISOPPFOLGINGSTILFELLE_URL = "https://isoppfolgingstilfelle"

private val log = LoggerFactory.getLogger("MockHttpClient")

fun <T> createMockHttpClient(
    expectedUrl: String,
    responseBody: T?,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    requiresAuthHeader: Boolean = true,
) = HttpClient(MockEngine) {
    install(ContentNegotiation) { jackson { applicationConfig() } }
    engine {
        addHandler { request ->
            request.url.toString() shouldBe expectedUrl
            if (requiresAuthHeader) request.headers[HttpHeaders.Authorization] shouldBe "Bearer XYZ"

            when (responseBody) {
                null -> {
                    respond(
                        content = "",
                        status = statusCode,
                    )
                }

                is ByteArray -> {
                    respond(
                        content = responseBody,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()),
                    )
                }

                else -> {
                    respond(
                        content = ByteReadChannel(objectMapper.writeValueAsBytes(responseBody)),
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
    }
}

fun mockHttpClient(defaultResponse: Any? = null): HttpClient {
    val mockEngine = MockEngine {
        val api = Pair(it.url.toString(), it.method)
        if (defaultResponse != null) MockResponseHandler.addResponse(it.url.toString(), it.method, defaultResponse)
        val response = MockResponseHandler.responses[api] ?: run {
            log.error("Reponse for ${api.second} ${api.first} mangler")
            throw NoSuchElementException("Response not mocked")
        }

        respond(
            content = ByteReadChannel(response.content),
            status = response.status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    return HttpClient(mockEngine) {
        install(ContentNegotiation) {
            jackson { applicationConfig() }
        }
    }
}

fun mockAmtArrangorClient(arrangor: Arrangor = TestData.lagArrangor()): AmtArrangorClient {
    val overordnetArrangor = arrangor.overordnetArrangorId?.let {
        TestData.lagArrangor(id = it)
    }

    val response = ArrangorResponse(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)
    return AmtArrangorClient(
        baseUrl = "https://amt-arrangor",
        scope = "amt.arrangor.scope",
        httpClient = mockHttpClient(objectMapper.writeValueAsString(response)),
        azureAdTokenClient = mockAzureAdClient(),
    )
}

fun mockIsOppfolgingstilfelleClient() = IsOppfolgingstilfelleClient(
    baseUrl = ISOPPFOLGINGSTILFELLE_URL,
    scope = "isoppfolgingstilfelle.scope",
    httpClient = mockHttpClient(),
    azureAdTokenClient = mockAzureAdClient(),
)

fun mockPersonServiceClient() = AmtPersonServiceClient(
    baseUrl = AMT_PERSON_URL,
    scope = "amt.person-service.scope",
    httpClient = mockHttpClient(),
    azureAdTokenClient = mockAzureAdClient(),
)

fun mockAzureAdClient() = AzureAdTokenClient(
    azureAdTokenUrl = "http://azure",
    clientId = "clientId",
    clientSecret = "secret",
    httpClient = mockHttpClient(
        """
        {
            "token_type":"Bearer",
            "access_token":"XYZ",
            "expires_in": 3599
        }
        """.trimIndent(),
    ),
)

object MockResponseHandler {
    data class Response(
        val content: String,
        val status: HttpStatusCode,
    )

    val responses = mutableMapOf<Pair<String, HttpMethod>, Response>()

    fun addResponse(
        url: String,
        method: HttpMethod,
        responseBody: Any,
        responseCode: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val api = Pair(url, method)
        responses[api] = Response(
            responseBody as? String ?: objectMapper.writeValueAsString(responseBody),
            responseCode,
        )
    }

    fun addNavAnsattResponse(navAnsatt: NavAnsatt) {
        val url = "$AMT_PERSON_URL/api/nav-ansatt/${navAnsatt.id}"
        addResponse(url, HttpMethod.Get, navAnsatt)
    }

    fun addNavAnsattPostResponse(navAnsatt: NavAnsatt) {
        val url = "$AMT_PERSON_URL/api/nav-ansatt"
        addResponse(url, HttpMethod.Post, navAnsatt)
    }

    fun addNavEnhetGetResponse(navEnhet: NavEnhet) {
        val url = "$AMT_PERSON_URL/api/nav-enhet/${navEnhet.id}"
        addResponse(url, HttpMethod.Get, NavEnhetDto(navEnhet.id, navEnhet.enhetsnummer, navEnhet.navn))
    }

    fun addNavEnhetResponse(navEnhet: NavEnhet) {
        val url = "$AMT_PERSON_URL/api/nav-enhet"
        addResponse(url, HttpMethod.Post, NavEnhetDto(navEnhet.id, navEnhet.enhetsnummer, navEnhet.navn))
    }

    fun addNavBrukerResponse(navBruker: NavBruker) {
        val url = "$AMT_PERSON_URL/api/nav-bruker"
        addResponse(url, HttpMethod.Post, navBruker)
    }

    fun addOppfolgingstilfelleRespons(oppfolgingstilfellePersonResponse: OppfolgingstilfellePersonResponse) {
        val url = "$ISOPPFOLGINGSTILFELLE_URL/api/system/v1/oppfolgingstilfelle/personident"
        addResponse(url, HttpMethod.Get, oppfolgingstilfellePersonResponse)
    }
}
