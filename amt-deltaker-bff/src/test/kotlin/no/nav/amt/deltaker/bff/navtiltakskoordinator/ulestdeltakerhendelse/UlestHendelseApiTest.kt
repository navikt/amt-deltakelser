package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class UlestHendelseApiTest : IntegrationTestBase() {
    @Test
    fun `skal returnere Unauthorized nar tilgang mangler`() {
        val response = withTestApplicationContext { client -> client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}") }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere NoContent nar hendelse er slettet`() {
        val ulestHendelseId = UUID.randomUUID()
        coEvery { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) } returns Unit
        every { ulestHendelseRepository.delete(ulestHendelseId) } just runs

        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/$ulestHendelseId") {
                bearerAuth(bearerTokenInTest)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent

        coVerify(exactly = 1) { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) }
        verify(exactly = 1) { ulestHendelseRepository.delete(ulestHendelseId) }
    }

    @Test
    fun `skal returnere NoContent og slette lokalt selv om sletting i amt-deltaker feiler`() {
        val ulestHendelseId = UUID.randomUUID()
        coEvery { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) } throws IllegalStateException("feil")
        every { ulestHendelseRepository.delete(ulestHendelseId) } just runs

        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/$ulestHendelseId") {
                bearerAuth(bearerTokenInTest)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent
        coVerify(exactly = 1) { tiltakskoordinatorClient.slettUlestHendelse(ulestHendelseId) }
        verify(exactly = 1) { ulestHendelseRepository.delete(ulestHendelseId) }
    }

    companion object {
        private fun lagUlestHendelse() = UlestHendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            deltakerId = UUID.randomUUID(),
            ansvarlig = null,
            hendelse = UlestHendelseType.NavGodkjennUtkast,
        )
    }
}
