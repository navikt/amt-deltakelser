package no.nav.amt.lib.ktor.auth

import com.github.benmanes.caffeine.cache.Cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.ktor.clients.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.CountingCache
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AzureAdTokenClientTest {
    @Nested
    inner class GetMachineToMachineToken {
        @Test
        fun `getMachineToMachineToken skal kaste feil hvis respons har feilkode`() {
            runFailureTest { adTokenClient ->
                adTokenClient.getMachineToMachineToken(FAKE_SCOPE)
            }
        }

        @Test
        fun `getMachineToMachineToken skal returnere token hvis respons er OK`() {
            runHappyPathTest(
                "Bearer $FAKE_TOKEN",
            ) { adTokenClient ->
                adTokenClient.getMachineToMachineToken(FAKE_SCOPE)
            }
        }

        @Test
        fun `getMachineToMachineToken skal token fra cache`(): Unit = runBlocking {
            val countingCache = CountingCache<String, AzureAdToken>()

            val client = createAzureAdTokenClient(
                statusCode = HttpStatusCode.OK,
                cache = countingCache,
            )

            client.getMachineToMachineToken(FAKE_SCOPE)
            client.getMachineToMachineToken(FAKE_SCOPE)

            countingCache.putCount shouldBe 1
        }
    }

    @Nested
    inner class GetMachineToMachineTokenWithoutType {
        @Test
        fun `getMachineToMachineTokenWithoutType skal kaste feil hvis respons har feilkode`() {
            runFailureTest { adTokenClient ->
                adTokenClient.getMachineToMachineTokenWithoutType(FAKE_SCOPE)
            }
        }

        @Test
        fun `getMachineToMachineTokenWithoutType skal returnere token hvis respons er OK`() {
            runHappyPathTest(
                FAKE_TOKEN,
            ) { adTokenClient ->
                adTokenClient.getMachineToMachineTokenWithoutType(FAKE_SCOPE)
            }
        }

        @Test
        fun `getMachineToMachineTokenWithoutType skal token fra cache`(): Unit = runBlocking {
            val countingCache = CountingCache<String, AzureAdToken>()

            val client = createAzureAdTokenClient(
                statusCode = HttpStatusCode.OK,
                cache = countingCache,
            )

            client.getMachineToMachineTokenWithoutType(FAKE_SCOPE)
            client.getMachineToMachineTokenWithoutType(FAKE_SCOPE)

            countingCache.putCount shouldBe 1
        }
    }

    companion object {
        private const val AZURE_AD_TOKEN_URL = "https://fake-url.com/token"
        private const val FAKE_SCOPE = "fake-scope"
        private const val FAKE_TOKEN = "XYZ"

        private val responseBodyInTest =
            """
            {
                "token_type":"Bearer",
                "access_token":"XYZ",
                "expires_in": 3599
            }
            """.trimIndent()

        private fun runFailureTest(block: suspend (AzureAdTokenClient) -> Unit) {
            val thrown = runBlocking {
                shouldThrow<RuntimeException> {
                    block(createAzureAdTokenClient(HttpStatusCode.Unauthorized))
                }
            }
            thrown.message shouldStartWith "Kunne ikke hente AAD-token"
        }

        private fun runHappyPathTest(expectedResponse: String, block: suspend (AzureAdTokenClient) -> String) = runBlocking {
            val azureAdTokenClient = createAzureAdTokenClient(HttpStatusCode.OK)
            block(azureAdTokenClient) shouldBe expectedResponse
        }

        private fun createAzureAdTokenClient(statusCode: HttpStatusCode = HttpStatusCode.OK, cache: Cache<String, AzureAdToken>? = null) =
            if (cache == null) {
                AzureAdTokenClient(
                    azureAdTokenUrl = AZURE_AD_TOKEN_URL,
                    clientId = "fake-client-id",
                    clientSecret = "fake-client-secret",
                    httpClient = createMockHttpClient(AZURE_AD_TOKEN_URL, responseBodyInTest, statusCode),
                )
            } else {
                AzureAdTokenClient(
                    azureAdTokenUrl = AZURE_AD_TOKEN_URL,
                    clientId = "fake-client-id",
                    clientSecret = "fake-client-secret",
                    httpClient = createMockHttpClient(AZURE_AD_TOKEN_URL, responseBodyInTest, statusCode),
                    tokenCache = cache,
                )
            }
    }
}
