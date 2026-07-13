package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Test
import java.util.UUID

class UlestHendelseApiTest : IntegrationTestBase() {
    @Test
    fun `skal returnere Unauthorized når tilgang mangler`() {
        val response = withTestApplicationContext { client -> client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere NoContent når hendelse er slettet`() {
        val ulestHendelseId = UUID.randomUUID()
        coEvery { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) } returns Unit

        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/$ulestHendelseId") {
                bearerAuth(bearerTokenInTest)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent

        coVerify(exactly = 1) { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) }
    }
}
