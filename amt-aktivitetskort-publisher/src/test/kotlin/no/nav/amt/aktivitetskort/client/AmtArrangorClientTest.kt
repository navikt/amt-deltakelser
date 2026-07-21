package no.nav.amt.aktivitetskort.client

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.TestUtils.staticObjectMapper
import no.nav.amt.aktivitetskort.database.TestData
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AmtArrangorClientTest {
    private lateinit var client: AmtArrangorClient
    private lateinit var server: MockWebServer
    private val token = "TOKEN"

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        client = AmtArrangorClient(
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
    fun `hentArrangor - arrangor finnes - parser response og returnerer arrangor`() {
        val overordnetArrangor = TestData.lagArrangor(navn = "Overordnet Arrangor")
        val arrangor = TestData.lagArrangor(overordnetArrangorId = overordnetArrangor.id)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                	"id": "${arrangor.id}",
                	"organisasjonsnummer": "${arrangor.organisasjonsnummer}",
                	"navn": "${arrangor.navn}",
                	"overordnetArrangor": {
                		"id": "${overordnetArrangor.id}",
                		"organisasjonsnummer": "${overordnetArrangor.organisasjonsnummer}",
                		"navn": "${overordnetArrangor.navn}",
                		"overordnetArrangorId": null
                	}
                }
                """.trimIndent(),
            ),
        )

        client.hentArrangor(arrangor.organisasjonsnummer) shouldBe AmtArrangorClient.ArrangorMedOverordnetArrangorDto(
            id = arrangor.id,
            navn = arrangor.navn,
            organisasjonsnummer = arrangor.organisasjonsnummer,
            overordnetArrangor = overordnetArrangor,
        )
    }

    @Test
    fun `hentArrangor - arrangor finnes ikke - skal ikke skje, kaster RuntimeException`() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows<RuntimeException> {
            client.hentArrangor("foo")
        }
    }
}
