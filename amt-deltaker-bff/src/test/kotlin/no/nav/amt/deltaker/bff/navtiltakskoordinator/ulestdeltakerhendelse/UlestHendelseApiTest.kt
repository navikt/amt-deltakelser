package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Test
import java.util.UUID

class UlestHendelseApiTest : IntegrationTestBase() {
    @Test
    fun `skal returnere Unauthorized nar tilgang mangler`() {
        val response = withTestApplicationContext { client -> client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere NoContent nar hendelse er slettet`() {
        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") {
                bearerAuth(bearerTokenInTest)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent

        coVerify { tiltakskoordinatorClient.slettUlestHendelse(any()) }
    }
}
