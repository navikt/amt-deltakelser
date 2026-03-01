package no.nav.amt.distribusjon.amtdeltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.createMockHttpClient
import no.nav.amt.distribusjon.utils.data.DeltakerData.lagDeltakerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import org.junit.jupiter.api.Test
import java.util.UUID

class AmtDeltakerClientTest : ClientTestBase() {
    @Test
    fun `skal returnere deltakerliste nar getDeltaker kalles med gyldig respons`() {
        val expectedDeltaker = lagDeltakerResponse()

        val sut = createAmtDeltakerClient(
            responseBody = expectedDeltaker,
        )

        val actualDeltaker = runBlocking {
            sut.getDeltaker(deltakerId)
        }

        actualDeltaker.gjennomforing shouldBe expectedDeltaker.gjennomforing
    }

    @Test
    fun `skal kaste feil nar getDeltaker returnerer feilkode`() {
        val sut = createAmtDeltakerClient(
            HttpStatusCode.BadRequest,
        )

        val thrown = runBlocking {
            shouldThrow<IllegalStateException> {
                sut.getDeltaker(deltakerId)
            }
        }

        thrown.message shouldStartWith "Kunne ikke hente deltaker fra amt-deltaker."
    }

    private fun createAmtDeltakerClient(statusCode: HttpStatusCode = HttpStatusCode.OK, responseBody: DeltakerResponse? = null) =
        AmtDeltakerClient(
            httpClient = createMockHttpClient(ENDRINGSVEDTAK_URL, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdTokenClient,
            environment = testEnvironment,
        )

    companion object {
        private val deltakerId: UUID = UUID.randomUUID()
        private val ENDRINGSVEDTAK_URL = "http://localhost/deltaker/$deltakerId"
    }
}
