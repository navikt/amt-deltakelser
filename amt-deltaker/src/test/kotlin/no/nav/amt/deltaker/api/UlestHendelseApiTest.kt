package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseFlags
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseTypeCounts
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.generateJWT
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class UlestHendelseApiTest : IntegrationTestBase() {
    @Test
    fun `skal returnere Unauthorized nar token mangler`() {
        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/${UUID.randomUUID()}")
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `get ulest hendelser for deltaker - returnerer 200 og data`() {
        val deltakerId = UUID.randomUUID()
        val expected = listOf(lagUlestHendelse(deltakerId))
        every { ulestHendelseRepository.getForDeltaker(deltakerId) } returns expected

        val responseBody = withTestApplicationContext { client ->
            client
                .get("/tiltakskoordinator/ulest-hendelse/$deltakerId") {
                    bearerAuth(systemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                }.body<String>()
        }

        responseBody shouldBe objectMapper.writeValueAsString(expected)
        verify(exactly = 1) { ulestHendelseRepository.getForDeltaker(deltakerId) }
    }

    @Test
    fun `get ulest hendelser for deltaker - ugyldig uuid - returnerer 400`() {
        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/ulest-hendelse/ikke-en-uuid") {
                bearerAuth(systemToken)
            }
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `post deltakere - returnerer flags per deltaker`() {
        val deltakerId = UUID.randomUUID()
        val expected = mapOf(deltakerId to UlestHendelseFlags(erNyDeltaker = true, harOppdateringFraNav = false))
        every { ulestHendelseRepository.getForDeltakere(setOf(deltakerId)) } returns expected

        val responseBody = withTestApplicationContext { client ->
            client
                .post("/tiltakskoordinator/ulest-hendelse/deltakere") {
                    bearerAuth(systemToken)
                    contentType(ContentType.Application.Json)
                    setBody(objectMapper.writeValueAsString(listOf(deltakerId)))
                }.apply {
                    status shouldBe HttpStatusCode.OK
                }.body<String>()
        }

        responseBody shouldBe objectMapper.writeValueAsString(expected)
        verify(exactly = 1) { ulestHendelseRepository.getForDeltakere(setOf(deltakerId)) }
    }

    @Test
    fun `post type counts - returnerer summerte tellinger`() {
        val deltakerId = UUID.randomUUID()
        val expected = UlestHendelseTypeCounts(erNyDeltaker = 1, harOppdateringFraNav = 2)
        every { ulestHendelseRepository.getTypeCountsForDeltakere(setOf(deltakerId)) } returns expected

        val responseBody = withTestApplicationContext { client ->
            client
                .post("/tiltakskoordinator/ulest-hendelse/type-counts") {
                    bearerAuth(systemToken)
                    contentType(ContentType.Application.Json)
                    setBody(objectMapper.writeValueAsString(listOf(deltakerId)))
                }.apply {
                    status shouldBe HttpStatusCode.OK
                }.body<String>()
        }

        responseBody shouldBe objectMapper.writeValueAsString(expected)
        verify(exactly = 1) { ulestHendelseRepository.getTypeCountsForDeltakere(setOf(deltakerId)) }
    }

    @Test
    fun `post upsert - upserter uleste hendelser`() {
        val ulesteHendelser = listOf(lagUlestHendelse(UUID.randomUUID()), lagUlestHendelse(UUID.randomUUID()))
        every { ulestHendelseRepository.upsertMany(ulesteHendelser) } just runs

        val responseBody = withTestApplicationContext { client ->
            client
                .post("/internal/tiltakskoordinator/ulest-hendelse/upsert") {
                    bearerAuth(systemToken)
                    contentType(ContentType.Application.Json)
                    setBody(objectMapper.writeValueAsString(ulesteHendelser))
                }.apply {
                    status shouldBe HttpStatusCode.OK
                }.body<String>()
        }

        responseBody shouldBe """{"upserted":2}"""
        verify(exactly = 1) { ulestHendelseRepository.upsertMany(ulesteHendelser) }
    }

    @Test
    fun `delete ulest hendelse - returnerer NoContent`() {
        val ulestHendelseId = UUID.randomUUID()
        every { ulestHendelseRepository.delete(ulestHendelseId) } returns Unit

        val response = withTestApplicationContext { client ->
            client.delete("/tiltakskoordinator/ulest-hendelse/$ulestHendelseId") {
                bearerAuth(systemToken)
            }
        }

        response.status shouldBe HttpStatusCode.NoContent
        verify(exactly = 1) { ulestHendelseRepository.delete(ulestHendelseId) }
    }

    companion object {
        private val systemToken = generateJWT(
            consumerClientId = "amt-deltaker-bff",
            audience = "amt-deltaker",
        )

        private fun lagUlestHendelse(deltakerId: UUID) = UlestHendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.of(2026, 7, 8, 11, 0),
            deltakerId = deltakerId,
            ansvarlig = AnsvarligNavnOgEnhet("Saksbehandler", "1234"),
            hendelse = UlestHendelseType.NavGodkjennUtkast,
        )
    }
}
