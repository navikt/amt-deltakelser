package no.nav.amt.deltaker.bff.utils

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
import no.nav.amt.deltaker.bff.apiclients.arrangor.ArrangorResponse
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.paamelding.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.OpprettKladdResponse
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

const val AMT_DELTAKER_URL = "http://amt-deltaker"
const val AMT_PERSON_SERVICE_URL = "http://amt-person-service"

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
    val mockEngine =
        MockEngine {
            val api = Pair(it.url.toString(), it.method)
            if (defaultResponse != null) MockResponseHandler.addResponse(it.url.toString(), it.method, defaultResponse)
            val response = MockResponseHandler.responses[api] ?: throw NoSuchElementException("Ingen response mocket for api $api")

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

fun mockAmtArrangorClient(arrangor: Arrangor = lagArrangor()): AmtArrangorClient {
    val overordnetArrangor = arrangor.overordnetArrangorId?.let { lagArrangor(id = it) }

    val response = ArrangorResponse(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)
    return AmtArrangorClient(
        baseUrl = "https://amt-arrangor",
        scope = "amt.arrangor.scope",
        httpClient = mockHttpClient(objectMapper.writeValueAsString(response)),
        azureAdTokenClient = mockAzureAdClient(),
    )
}

fun mockAmtDeltakerClient() =
    AmtDeltakerClient(
        baseUrl = AMT_DELTAKER_URL,
        scope = "amt.deltaker.scope",
        httpClient = mockHttpClient(),
        azureAdTokenClient = mockAzureAdClient(),
    )

fun mockPaameldingClient() =
    PaameldingClient(
        baseUrl = AMT_DELTAKER_URL,
        scope = "amt.deltaker.scope",
        httpClient = mockHttpClient(),
        azureAdTokenClient = mockAzureAdClient(),
    )

fun mockAmtPersonServiceClient(): AmtPersonServiceClient =
    AmtPersonServiceClient(
        baseUrl = AMT_PERSON_SERVICE_URL,
        scope = "amt.personservice.scope",
        httpClient = mockHttpClient(),
        azureAdTokenClient = mockAzureAdClient(),
    )

fun mockAzureAdClient() =
    AzureAdTokenClient(
        azureAdTokenUrl = "http://azure",
        clientId = "clientId",
        clientSecret = "secret",
        httpClient =
            mockHttpClient(
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
        responseBody: Any = "",
        responseCode: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val api = Pair(url, method)

        responses[api] =
            Response(
                responseBody as? String ?: objectMapper.writeValueAsString(responseBody),
                responseCode,
            )
    }

    fun addOpprettKladdResponse(deltaker: Deltaker?) {
        val url = "$AMT_DELTAKER_URL/pamelding"
        if (deltaker == null) {
            addResponse(url, HttpMethod.Post, "Noe gikk galt", HttpStatusCode.InternalServerError)
        } else {
            addResponse(
                url = url,
                method = HttpMethod.Post,
                responseBody =
                    OpprettKladdResponse(
                        id = deltaker.id,
                        navBruker = deltaker.navBruker,
                        deltakerlisteId = deltaker.deltakerliste.id,
                        startdato = deltaker.startdato,
                        sluttdato = deltaker.sluttdato,
                        dagerPerUke = deltaker.dagerPerUke,
                        deltakelsesprosent = deltaker.deltakelsesprosent,
                        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
                        deltakelsesinnhold = deltaker.deltakelsesinnhold!!,
                        status = deltaker.status,
                    ),
            )
        }
    }

    fun addUtkastResponse(deltaker: Deltaker) {
        val url = "$AMT_DELTAKER_URL/pamelding/${deltaker.id}"
        addResponse(
            url = url,
            method = HttpMethod.Post,
            responseBody = deltaker.toDeltakeroppdatering(),
        )
    }

    fun avbrytUtkastResponse(deltaker: Deltaker) {
        val url = "$AMT_DELTAKER_URL/pamelding/${deltaker.id}/avbryt"
        addResponse(
            url = url,
            method = HttpMethod.Post,
            responseBody = deltaker.toDeltakeroppdatering(),
        )
    }

    fun addSlettKladdResponse(deltakerId: UUID) {
        val url = "$AMT_DELTAKER_URL/pamelding/$deltakerId"
        addResponse(
            url = url,
            method = HttpMethod.Delete,
        )
    }

    fun addEndringsresponse(
        deltaker: Deltaker,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val url = "$AMT_DELTAKER_URL/deltaker/${deltaker.id}/${AmtDeltakerClient.ENDRE_DELTAKER_URL_SEGMENT}"

        addResponse(
            url = url,
            method = HttpMethod.Post,
            responseBody = deltaker.toDeltakeroppdatering(),
            responseCode = status,
        )
    }

    fun addInnbyggerGodkjennUtkastResponse(
        deltaker: Deltaker,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val url = "$AMT_DELTAKER_URL/pamelding/${deltaker.id}/innbygger/godkjenn-utkast"
        addResponse(url, HttpMethod.Post, deltaker.toDeltakeroppdatering(), status)
    }

    fun addNavAnsattResponse(
        navAnsatt: NavAnsatt,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val url = "$AMT_PERSON_SERVICE_URL/api/nav-ansatt/${navAnsatt.id}"
        addResponse(url, HttpMethod.Get, navAnsatt, status)
    }

    fun addNavEnhetGetResponse(
        navEnhet: NavEnhet,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val url = "$AMT_PERSON_SERVICE_URL/api/nav-enhet/${navEnhet.id}"
        addResponse(url, HttpMethod.Get, navEnhet.toDto(), status)
    }

    fun addNavEnhetPostResponse(
        navEnhet: NavEnhet,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val url = "$AMT_PERSON_SERVICE_URL/api/nav-enhet"
        addResponse(url, HttpMethod.Post, navEnhet.toDto(), status)
    }
}

fun NavEnhet.toDto() =
    NavEnhetDto(
        id,
        enhetsnummer,
        navn,
    )
