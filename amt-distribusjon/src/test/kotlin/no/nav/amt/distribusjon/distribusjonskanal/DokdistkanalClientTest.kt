package no.nav.amt.distribusjon.distribusjonskanal

import com.github.benmanes.caffeine.cache.Cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.CountingCache
import org.junit.jupiter.api.Test
import java.util.UUID

class DokdistkanalClientTest {
    @Test
    fun `skal returnere DITT_NAV nar bestemDistribusjonskanal kalles med `() = runTest {
        val sut = createDokdistkanalClient(
            responseBody = expectedResponse,
        )

        val actualResponse = sut.bestemDistribusjonskanal(PERSON_IDENT, deltakerId)

        actualResponse shouldBe expectedResponse.distribusjonskanal
    }

    @Test
    fun `skal bruke cache ved andre kall til bestemDistribusjonskanal`() = runTest {
        val countingCache = CountingCache<String, Distribusjonskanal>()

        val sut = createDokdistkanalClient(
            responseBody = expectedResponse,
            cache = countingCache,
        )

        sut.bestemDistribusjonskanal(PERSON_IDENT, null)
        sut.bestemDistribusjonskanal(PERSON_IDENT, null)

        countingCache.putCount shouldBe 1
    }

    @Test
    fun `skal kaste feil nar bestemDistribusjonskanal returnerer feilkode, deltakerId != null`() = runTest {
        val sut = createDokdistkanalClient(statusCode = HttpStatusCode.BadGateway)

        val thrown = shouldThrow<IllegalStateException> {
            sut.bestemDistribusjonskanal(PERSON_IDENT, deltakerId)
        }

        thrown.message shouldStartWith "Kunne ikke hente distribusjonskanal for deltaker"
    }

    @Test
    fun `skal kaste feil nar bestemDistribusjonskanal returnerer feilkode, deltakerId = null`() = runTest {
        val sut = createDokdistkanalClient(statusCode = HttpStatusCode.BadGateway)

        val thrown = shouldThrow<IllegalStateException> {
            sut.bestemDistribusjonskanal(PERSON_IDENT, null)
        }

        thrown.message shouldStartWith "Kunne ikke hente distribusjonskanal, status"
    }

    private fun createDokdistkanalClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBody: DokdistkanalClient.BestemDistribusjonskanalResponse? = null,
        cache: Cache<String, Distribusjonskanal>? = null,
    ): DokdistkanalClient {
        val httpClient = createMockHttpClient(
            expectedUrl = "http://dokdistkanal/rest/bestemDistribusjonskanal",
            statusCode = statusCode,
            responseBody = responseBody,
        )

        return if (cache != null) {
            DokdistkanalClient(
                httpClient = httpClient,
                azureAdTokenClient = mockAzureAdClient(),
                environment = testEnvironment,
                distribusjonskanalCache = cache,
            )
        } else {
            DokdistkanalClient(
                httpClient = httpClient,
                azureAdTokenClient = mockAzureAdClient(),
                environment = testEnvironment,
            )
        }
    }

    companion object {
        private const val PERSON_IDENT = "~personident~"
        private val deltakerId: UUID = UUID.randomUUID()
        private val expectedResponse = DokdistkanalClient.BestemDistribusjonskanalResponse(Distribusjonskanal.DITT_NAV)
    }
}
