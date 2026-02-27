package no.nav.amt.distribusjon.utils

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.toByteArray
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.application.plugins.applicationConfig
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import no.nav.amt.distribusjon.distribusjonskanal.BestemDistribusjonskanalRequest
import no.nav.amt.distribusjon.distribusjonskanal.BestemDistribusjonskanalResponse
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.distribusjonskanal.DokdistkanalClient
import no.nav.amt.distribusjon.journalforing.dokarkiv.DokarkivClient
import no.nav.amt.distribusjon.journalforing.dokarkiv.OpprettJournalpostResponse
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DistribuerJournalpostResponse
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient
import no.nav.amt.distribusjon.journalforing.pdf.PdfgenClient
import no.nav.amt.distribusjon.journalforing.person.AmtPersonClient
import no.nav.amt.distribusjon.journalforing.person.NavBrukerRequest
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.data.DeltakerData
import no.nav.amt.distribusjon.utils.data.Journalforingdata
import no.nav.amt.distribusjon.utils.data.Persondata
import no.nav.amt.distribusjon.veilarboppfolging.ManuellStatusRequest
import no.nav.amt.distribusjon.veilarboppfolging.ManuellV2Response
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import java.util.UUID

fun <T> createMockHttpClient(
    expectedUrl: String,
    responseBody: T?,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    requiresAuthHeader: Boolean = true,
) = HttpClient(MockEngine) {
    install(ContentNegotiation) {
        jackson { applicationConfig() }
    }
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
                        content = objectMapper.writeValueAsString(responseBody),
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
        val body = when (it.body) {
            is TextContent -> {
                (it.body as TextContent).text
            }

            is ByteArrayContent -> {
                (it.body as ByteArrayContent).toByteArray().decodeToString()
            }

            else -> {
                null
            }
        }

        val request = MockResponseHandler.Request(it.url.toString(), it.method, body)

        val response = MockResponseHandler.responses.getOrPut(request) {
            MockResponseHandler.Response(
                if (defaultResponse is String) defaultResponse else objectMapper.writeValueAsString(defaultResponse),
                HttpStatusCode.OK,
            )
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

fun mockAzureAdClient(environment: Environment) = AzureAdTokenClient(
    httpClient = mockHttpClient(
        """
        {
            "token_type":"Bearer",
            "access_token":"XYZ",
            "expires_in": 3599
        }
        """.trimIndent(),
    ),
    environment,
)

fun mockPdfgenClient(environment: Environment) = PdfgenClient(
    mockHttpClient("%PDF-".toByteArray()),
    environment,
)

fun mockAmtPersonClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = AmtPersonClient(
    mockHttpClient(Persondata.lagNavBruker()),
    azureAdTokenClient,
    environment,
)

fun mockVeilarboppfolgingClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = VeilarboppfolgingClient(
    mockHttpClient(Journalforingdata.lagSak()),
    azureAdTokenClient,
    environment,
)

fun mockDokarkivClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = DokarkivClient(
    mockHttpClient(OpprettJournalpostResponse((100_000..999_999).random().toString())),
    azureAdTokenClient,
    environment,
)

fun mockDokdistkanalClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = DokdistkanalClient(
    mockHttpClient(BestemDistribusjonskanalResponse(Distribusjonskanal.DITT_NAV)),
    azureAdTokenClient,
    environment,
)

fun mockDokdistfordelingClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = DokdistfordelingClient(
    mockHttpClient(DistribuerJournalpostResponse(UUID.randomUUID())),
    azureAdTokenClient,
    environment,
)

fun mockAmtDeltakerClient(azureAdTokenClient: AzureAdTokenClient, environment: Environment) = AmtDeltakerClient(
    mockHttpClient(DeltakerData.lagDeltakerResponse()),
    azureAdTokenClient,
    environment,
)

object MockResponseHandler {
    data class Request(
        val url: String,
        val method: HttpMethod,
        val body: String?,
    )

    data class Response(
        val content: String,
        val status: HttpStatusCode,
    )

    val responses = mutableMapOf<Request, Response>()

    fun addResponse(
        request: Request,
        responseBody: Any = "",
        responseCode: HttpStatusCode = HttpStatusCode.OK,
    ) {
        responses[request] = Response(
            responseBody as? String ?: objectMapper.writeValueAsString(responseBody),
            responseCode,
        )
    }

    fun addDeltakerResponse(deltaker: DeltakerResponse) = addResponse(
        Request(
            url = "${testEnvironment.amtDeltakerUrl}/deltaker/${deltaker.id}",
            method = HttpMethod.Get,
            body = null,
        ),
        deltaker,
    )

    fun addDistribusjonskanalResponse(personident: String, distribusjonskanal: Distribusjonskanal) = post(
        "${testEnvironment.dokdistkanalUrl}/rest/bestemDistribusjonskanal",
        BestemDistribusjonskanalRequest(personident),
        BestemDistribusjonskanalResponse(distribusjonskanal),
    )

    fun addDistribusjonskanalErrorResponse(personident: String, status: HttpStatusCode) = post(
        url = "${testEnvironment.dokdistkanalUrl}/rest/bestemDistribusjonskanal",
        requestBody = BestemDistribusjonskanalRequest(personident),
        responseCode = status,
    )

    fun addNavBrukerResponse(personident: String, navBruker: NavBruker) = post(
        "${testEnvironment.amtPersonUrl}/api/nav-bruker",
        NavBrukerRequest(personident),
        navBruker,
    )

    fun addManuellOppfolgingResponse(personident: String, manuellOppfolging: Boolean) = post(
        "${testEnvironment.veilarboppfolgingUrl}/veilarboppfolging/api/v3/hent-manuell",
        ManuellStatusRequest(personident),
        ManuellV2Response(manuellOppfolging),
    )

    fun addManuellOppfolgingErrorResponse(personident: String, status: HttpStatusCode) = post(
        url = "${testEnvironment.veilarboppfolgingUrl}/veilarboppfolging/api/v3/hent-manuell",
        requestBody = ManuellStatusRequest(personident),
        responseCode = status,
    )

    private fun post(
        url: String,
        requestBody: Any?,
        responseBody: Any = "",
        responseCode: HttpStatusCode = HttpStatusCode.OK,
    ) = addResponse(
        Request(url, HttpMethod.Post, requestBody?.let { objectMapper.writeValueAsString(it) }),
        responseBody,
        responseCode,
    )
}
