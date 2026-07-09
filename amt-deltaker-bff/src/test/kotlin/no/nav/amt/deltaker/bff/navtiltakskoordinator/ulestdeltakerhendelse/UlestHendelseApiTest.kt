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
import no.nav.amt.deltaker.bff.utils.generateJWT
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

    @Test
    fun `skal returnere Forbidden for intern sync nar request ikke er intern`() {
        val response = withTestApplicationContext { client ->
            client.post("/internal/tiltakskoordinator/ulest-hendelse/sync?fom=0&tom=9")
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `skal synce uleste hendelser for valgt intervall`() {
        val fom = 10
        val tom = 19
        val hendelser = listOf(
            lagUlestHendelse(),
            lagUlestHendelse(),
        )
        every { ulestHendelseRepository.getRangeOrderedByOpprettet(fom, tom - fom + 1) } returns hendelser
        coEvery { tiltakskoordinatorClient.upsertUlesteHendelser(hendelser) } returns hendelser.size

        val response = withTestApplicationContext { client ->
            client.post("http://127.0.0.1/internal/tiltakskoordinator/ulest-hendelse/sync?fom=$fom&tom=$tom")
        }

        response.status shouldBe HttpStatusCode.OK
        verify(exactly = 1) { ulestHendelseRepository.getRangeOrderedByOpprettet(fom, tom - fom + 1) }
        coVerify(exactly = 1) { tiltakskoordinatorClient.upsertUlesteHendelser(hendelser) }
    }

    @Test
    fun `skal returnere BadRequest nar sync-parametere er ugyldige`() {
        val response = withTestApplicationContext { client ->
            client.post("http://127.0.0.1/internal/tiltakskoordinator/ulest-hendelse/sync?fom=5&tom=2")
        }

        response.status shouldBe HttpStatusCode.BadRequest
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
