package no.nav.amt.distribusjon.journalforing.dokarkiv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.createMockHttpClient
import no.nav.amt.distribusjon.utils.withLogCapture
import no.nav.amt.distribusjon.veilarboppfolging.Sak
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClientTest.Companion.oppfolgingsperiodeId
import org.junit.jupiter.api.Test
import java.util.UUID

class DokarkivClientTest : ClientTestBase() {
    @Test
    fun `skal returnere journalpostId nar opprettJournalpost kalles med gyldig respons`() {
        val sut = createDokarkivClient(
            responseBody = expectedResponse,
        )

        val actualResponse: String = sut.runOpprettJournalpostWithTestParams()

        actualResponse shouldBe expectedResponse.journalpostId
    }

    @Test
    fun `skal skrive warning til loggene nar journalpost for hendelseId allerede er opprettet`() {
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
    fun `skal kaste feil nar opprettJournalpost returnerer feilkode`() {
        val sut = createDokarkivClient(
            HttpStatusCode.BadRequest,
        )

        val thrown = shouldThrow<IllegalStateException> {
            sut.runOpprettJournalpostWithTestParams()
        }

        thrown.message shouldStartWith "Kunne ikke opprette journalpost for hendelseId $expectedHendelseId"
    }

    private fun createDokarkivClient(statusCode: HttpStatusCode = HttpStatusCode.OK, responseBody: OpprettJournalpostResponse? = null) =
        DokarkivClient(
            httpClient = createMockHttpClient(
                expectedUrl = "http://localhost/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true",
                responseBody = responseBody,
                statusCode = statusCode,
            ),
            azureAdTokenClient = mockAzureAdTokenClient,
            environment = testEnvironment,
        )

    companion object {
        private val expectedHendelseId: UUID = UUID.randomUUID()
        private val expectedResponse = OpprettJournalpostResponse("~journalpostId~")

        private fun DokarkivClient.runOpprettJournalpostWithTestParams(): String = runBlocking {
            opprettJournalpost(
                hendelseId = expectedHendelseId,
                fnr = "~fnr~",
                sak = Sak(oppfolgingsperiodeId, sakId = 42, fagsaksystem = "~fagsaksystem~"),
                pdf = "Hello World".toByteArray(),
                journalforendeEnhet = "~journalforendeEnhet~",
                journalpostNavn = "~journalpostNavn~",
            )
        }
    }
}
