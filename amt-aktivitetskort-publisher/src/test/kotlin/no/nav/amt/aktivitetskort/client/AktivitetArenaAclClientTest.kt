package no.nav.amt.aktivitetskort.client

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.TestUtils.staticObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class AktivitetArenaAclClientTest {
    private lateinit var client: AktivitetArenaAclClient
    private lateinit var server: MockWebServer
    private val token = "TOKEN"

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        client = AktivitetArenaAclClient(
            baseUrl = server.url("").toString().removeSuffix("/"),
            tokenProvider = { token },
            objectMapper = staticObjectMapper,
        )
    }

    @AfterEach
    fun cleanup() {
        server.shutdown()
    }

    @Test
    fun `getAktivitetIdForArenaId - returnerer id om eksisterer`() {
        val response = UUID.randomUUID()
        server.enqueue(MockResponse().setBody(staticObjectMapper.writeValueAsString(response)))

        val id = client.getAktivitetIdForArenaId(1L)

        id shouldBe response
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster feilmelding om 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows<IllegalStateException> {
            client.getAktivitetIdForArenaId(1L)
        }
    }
}
