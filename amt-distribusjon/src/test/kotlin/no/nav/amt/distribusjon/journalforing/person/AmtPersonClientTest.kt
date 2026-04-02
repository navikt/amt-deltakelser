package no.nav.amt.distribusjon.journalforing.person

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.data.Persondata.lagNavBruker
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import org.junit.jupiter.api.Test

class AmtPersonClientTest : ClientTestBase() {
    @Test
    fun `skal returnere NavBruker nar hentNavBruker kalles med gyldig respons`() = runTest {
        val expectedResponse = lagNavBruker()

        val sut: AmtPersonClient = createAmtPersonClient(
            responseBody = expectedResponse,
        )

        val actualResponse = sut.hentNavBruker("~personident~")

        actualResponse shouldBe expectedResponse
    }

    @Test
    fun `skal kaste feil nar hentNavBruker returnerer feilkode`() = runTest {
        val sut = createAmtPersonClient(
            responseBody = null,
            statusCode = HttpStatusCode.BadRequest,
        )

        val thrown = shouldThrow<IllegalStateException> {
            sut.hentNavBruker("~personident~")
        }

        thrown.message shouldBe "Kunne ikke hente nav-bruker fra amt-person-service"
    }

    private fun createAmtPersonClient(
        responseBody: NavBruker?,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ) = AmtPersonClient(
        httpClient = createMockHttpClient(
            expectedUrl = "http://amt-person/api/nav-bruker",
            responseBody = responseBody,
            statusCode = statusCode,
        ),
        azureAdTokenClient = mockAzureAdTokenClient,
        environment = testEnvironment,
    )
}
