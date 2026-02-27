package no.nav.amt.distribusjon.distribusjonskanal

import com.github.benmanes.caffeine.cache.Cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.CountingCache
import no.nav.amt.distribusjon.utils.createMockHttpClient
import org.junit.jupiter.api.Test
import java.util.UUID

class DokdistkanalClientTest : ClientTestBase() {
    @Test
    fun `skal returnere DITT_NAV nar bestemDistribusjonskanal kalles med `() {
        val sut = createDokdistkanalClient(
            responseBody = expectedResponse,
        )

        val actualResponse = runBlocking {
            sut.bestemDistribusjonskanal(PERSON_IDENT, deltakerId)
        }

        actualResponse shouldBe expectedResponse.distribusjonskanal
    }

    @Test
    fun `skal bruke cache ved andre kall til bestemDistribusjonskanal`() {
        val countingCache = CountingCache<String, Distribusjonskanal>()

        val sut = createDokdistkanalClient(
            responseBody = expectedResponse,
            cache = countingCache,
        )

        runBlocking {
            sut.bestemDistribusjonskanal(PERSON_IDENT, null)
            sut.bestemDistribusjonskanal(PERSON_IDENT, null)
        }

        countingCache.putCount shouldBe 1
    }

    @Test
    fun `skal kaste feil nar bestemDistribusjonskanal returnerer feilkode, deltakerId != null`() {
        val sut = createDokdistkanalClient(statusCode = HttpStatusCode.BadGateway)

        val thrown = runBlocking {
            shouldThrow<IllegalStateException> {
                sut.bestemDistribusjonskanal(PERSON_IDENT, deltakerId)
            }
        }

        thrown.message shouldStartWith "Kunne ikke hente distribusjonskanal for deltaker"
    }

    @Test
    fun `skal kaste feil nar bestemDistribusjonskanal returnerer feilkode, deltakerId = null`() {
        val sut = createDokdistkanalClient(statusCode = HttpStatusCode.BadGateway)

        val thrown = runBlocking {
            shouldThrow<IllegalStateException> {
                sut.bestemDistribusjonskanal(PERSON_IDENT, null)
            }
        }

        thrown.message shouldStartWith "Kunne ikke hente distribusjonskanal, status"
    }

    private fun createDokdistkanalClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBody: BestemDistribusjonskanalResponse? = null,
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
                azureAdTokenClient = mockAzureAdTokenClient,
                environment = testEnvironment,
                distribusjonskanalCache = cache,
            )
        } else {
            DokdistkanalClient(
                httpClient = httpClient,
                azureAdTokenClient = mockAzureAdTokenClient,
                environment = testEnvironment,
            )
        }
    }

    companion object {
        private const val PERSON_IDENT = "~personident~"
        private val deltakerId: UUID = UUID.randomUUID()
        private val expectedResponse = BestemDistribusjonskanalResponse(Distribusjonskanal.DITT_NAV)
    }
}
