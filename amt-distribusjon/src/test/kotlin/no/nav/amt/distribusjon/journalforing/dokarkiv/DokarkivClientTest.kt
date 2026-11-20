package no.nav.amt.distribusjon.journalforing.dokarkiv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.veilarboppfolging.Sak
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClientTest.Companion.oppfolgingsperiodeId
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.withLogCapture
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class DokarkivClientTest {
    @Test
    fun `skal returnere journalpostId når opprettJournalpost kalles med gyldig respons`() = runTest {
        val sut = createDokarkivClient(
            responseBody = expectedResponse,
        )

        val actualResponse: String = sut.runOpprettJournalpostWithTestParams()

        actualResponse shouldBe expectedResponse.journalpostId
    }

    @Test
    fun `skal skrive warning til loggene når journalpost for hendelseId allerede er opprettet`() = runTest {
        val sut = createDokarkivClient(
            statusCode = HttpStatusCode.Conflict,
            responseBody = expectedResponse,
        )

        withLogCapture("no.nav.amt.distribusjon.journalforing.dokarkiv.DokarkivClient") { logEvents ->
            sut.runOpprettJournalpostWithTestParams()

            val messages = logEvents.map { it.formattedMessage }
            messages.shouldNotBeEmpty()
            messages.any { it.contains("Journalpost for hendelseId $expectedHendelseId er allerede opprettet") } shouldBe true
        }
    }

    @Test
    fun `skal kaste feil når opprettJournalpost returnerer feilkode`() = runTest {
        val sut = createDokarkivClient(
            HttpStatusCode.BadRequest,
        )

        val thrown = shouldThrow<IllegalStateException> {
            sut.runOpprettJournalpostWithTestParams()
        }

        thrown.message shouldStartWith "Kunne ikke opprette journalpost for hendelseId $expectedHendelseId"
    }

    @Test
    fun `skal sende datoDokument til Dokarkiv`() = runTest {
        // Arrange
        val datoDokument = LocalDateTime.of(2026, 11, 8, 23, 17)
        var requestBody: String? = null
        val httpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { jackson { findAndAddModules() } }
            engine {
                addHandler { request ->
                    requestBody = (request.body as TextContent).text
                    respond(
                        content = ByteReadChannel(objectMapper.writeValueAsBytes(expectedResponse)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val sut = DokarkivClient(
            httpClient = httpClient,
            azureAdTokenClient = mockAzureAdClient(),
            environment = testEnvironment,
        )

        // Act
        sut.opprettJournalpost(
            hendelseId = expectedHendelseId,
            fnr = "~fnr~",
            sak = Sak(oppfolgingsperiodeId, sakId = 42, fagsaksystem = "~fagsaksystem~"),
            pdf = "Hello World".toByteArray(),
            journalforendeEnhet = "~journalforendeEnhet~",
            journalpostNavn = "~journalpostNavn~",
            datoDokument = datoDokument,
        )

        // Assert
        LocalDateTime.parse(objectMapper.readTree(checkNotNull(requestBody))["datoDokument"].asString()) shouldBe datoDokument
    }

    private fun createDokarkivClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBody: OpprettJournalpostResponse? = null,
    ) = DokarkivClient(
        httpClient = createMockHttpClient(
            expectedUrl = "http://localhost/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true",
            responseBody = responseBody,
            statusCode = statusCode,
        ),
        azureAdTokenClient = mockAzureAdClient(),
        environment = testEnvironment,
    )

    companion object {
        private val expectedHendelseId: UUID = UUID.randomUUID()
        private val expectedResponse = OpprettJournalpostResponse("~journalpostId~")

        private suspend fun DokarkivClient.runOpprettJournalpostWithTestParams(): String = opprettJournalpost(
            hendelseId = expectedHendelseId,
            fnr = "~fnr~",
            sak = Sak(oppfolgingsperiodeId, sakId = 42, fagsaksystem = "~fagsaksystem~"),
            pdf = "Hello World".toByteArray(),
            journalforendeEnhet = "~journalforendeEnhet~",
            journalpostNavn = "~journalpostNavn~",
            datoDokument = LocalDateTime.of(2026, 11, 8, 23, 17),
        )
    }
}
