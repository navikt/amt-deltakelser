package no.nav.amt.deltaker.bff.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import no.nav.amt.deltaker.bff.utils.RouteTestBase
import no.nav.amt.deltaker.bff.utils.generateJWT
import org.junit.jupiter.api.Test
import java.util.UUID

class UlestHendelseApiTest : RouteTestBase() {
    @Test
    fun `skal returnere Unauthorized nar tilgang mangler`() {
        val response = withTestApplicationContext { client -> client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere NoContent nar hendelse er slettet`() {
        every { ulestHendelseService.delete(any()) } just runs

        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") {
                bearerAuth(bearerTokenInTest)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent

        verify { ulestHendelseService.delete(any()) }
    }

    companion object {
        val bearerTokenInTest = generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
            groups = listOf(UUID(0L, 0L).toString()),
        )
    }
}
