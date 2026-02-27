package no.nav.amt.distribusjon.journalforing.dokdistfordeling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.createMockHttpClient
import no.nav.amt.distribusjon.utils.withLogCapture
import org.junit.jupiter.api.Test
import java.util.UUID

class DokdistfordelingClientTest : ClientTestBase() {
    @Test
    fun `skal returnere bestillingsId nar distribuerJournalpost kalles med gyldig respons`() {
        val sut = createDokdistfordelingClient(
            responseBody = expectedResponse,
        )

        val actualResponse = sut.runDistribuerJournalpostWithTestParams()

        actualResponse shouldBe bestillingsId
    }

    @Test
    fun `skal returnere bestillingsId og logge warning nar distribuerJournalpost kalles med respons som returnerer 409 Conflict`() {
        val sut = createDokdistfordelingClient(
            HttpStatusCode.Conflict,
            responseBody = expectedResponse,
        )

        withLogCapture("no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient") { logEvents ->
            val actualResponse = sut.runDistribuerJournalpostWithTestParams()
            actualResponse shouldBe bestillingsId

            val messages = logEvents.map { it.formattedMessage }
            messages.any { it.contains("Journalpost $JOURNAL_POST_ID er allerede distribuert") } shouldBe true
        }
    }

    @Test
    fun `skal returnere null og logge warning nar distribuerJournalpost kalles med respons som returnerer 410 Gone`() {
        val sut = createDokdistfordelingClient(
            HttpStatusCode.Gone,
            responseBody = expectedResponse,
        )

        withLogCapture("no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient") { logEvents ->
            val actualResponse = sut.runDistribuerJournalpostWithTestParams()
            actualResponse.shouldBeNull()

            val messages = logEvents.map { it.formattedMessage }
            messages.any {
                it.contains("Journalpost $JOURNAL_POST_ID tilhører bruker som er død og som mangler adresse i PDL. Kan ikke sende brev.")
            } shouldBe true
        }
    }

    @Test
    fun `skal kaste feil nar distribuerJournalpost kalles med response som returnerer feilkode forskjellig fra 409 og 410`() {
        val sut = createDokdistfordelingClient(
            HttpStatusCode.BadGateway,
        )

        val thrown = shouldThrow<IllegalStateException> {
            sut.runDistribuerJournalpostWithTestParams()
        }

        thrown.message shouldStartWith "Distribuering av journalpost $JOURNAL_POST_ID feilet"
    }

    private fun createDokdistfordelingClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBody: DistribuerJournalpostResponse? = null,
    ) = DokdistfordelingClient(
        httpClient = createMockHttpClient(
            expectedUrl = "http://localhost/rest/v1/distribuerjournalpost",
            responseBody = responseBody,
            statusCode = statusCode,
        ),
        azureAdTokenClient = mockAzureAdTokenClient,
        environment = testEnvironment,
    )

    companion object {
        private const val JOURNAL_POST_ID = "~journalpostId~"
        private val bestillingsId: UUID = UUID.randomUUID()
        private val expectedResponse = DistribuerJournalpostResponse(bestillingsId)

        private fun DokdistfordelingClient.runDistribuerJournalpostWithTestParams(): UUID? = runBlocking {
            distribuerJournalpost(
                journalpostId = JOURNAL_POST_ID,
                distribusjonstype = DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
                tvingSentralPrint = true,
            )
        }
    }
}
