package no.nav.tiltaksarrangor.client.amtarrangor

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.client.ClientTestConfig
import no.nav.tiltaksarrangor.client.RestClientTestBase
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.client.amtarrangor.dto.VeilederAnsatt
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@ActiveProfiles("test")
@RestClientTest(AmtArrangorClient::class)
class AmtArrangorClientTest(
    @Autowired private val sut: AmtArrangorClient,
) : RestClientTestBase("amt-arrangor-tokenx") {
    @Nested
    inner class GetAnsattTests {
        @Test
        fun `getAnsatt - returnerer ansatt ved suksess`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()
            val deltakerId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$ansattId",
                          "personalia": {
                            "personident": "12345678901",
                            "navn": {
                              "fornavn": "Test",
                              "mellomnavn": null,
                              "etternavn": "Testesen"
                            }
                          },
                          "arrangorer": [
                            {
                              "arrangorId": "$arrangorId",
                              "roller": ["KOORDINATOR", "VEILEDER"],
                              "veileder": [{"deltakerId": "$deltakerId", "type": "VEILEDER"}],
                              "koordinator": ["$deltakerlisteId"]
                            }
                          ]
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.getAnsatt()

            assertSoftly(result.shouldNotBeNull()) {
                id shouldBe ansattId
                personalia.personident shouldBe "12345678901"
                personalia.navn.fornavn shouldBe "Test"
                personalia.navn.etternavn shouldBe "Testesen"
                arrangorer.size shouldBe 1
                arrangorer[0].arrangorId shouldBe arrangorId
            }
        }

        @Test
        fun `getAnsatt - kaster ikke NoSuchElementException ved 404`() {
            server
                .expect(requestTo("/api/ansatt"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            val result = sut.getAnsatt()
            result shouldBe null
        }

        @Test
        fun `getAnsatt - kaster UnauthorizedException ved 403`() {
            server
                .expect(requestTo("/api/ansatt"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UnauthorizedException> {
                sut.getAnsatt()
            }.message shouldBe "Ikke tilgang til å hente ansatt fra amt-arrangør"
        }

        @Test
        fun `getAnsatt - kaster RuntimeException ved 500`() {
            server
                .expect(requestTo("/api/ansatt"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.getAnsatt()
            }.message shouldBe "Kunne ikke hente ansatt fra amt-arrangør."
        }
    }

    @Nested
    inner class LeggTilDeltakerlisteForKoordinatorTests {
        @Test
        fun `leggTilDeltakerlisteForKoordinator - sender POST og returnerer ved suksess`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andRespond(withSuccess())

            sut.leggTilDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)

            server.verify()
        }

        @Test
        fun `leggTilDeltakerlisteForKoordinator - kaster UnauthorizedException ved 403`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UnauthorizedException> {
                sut.leggTilDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)
            }.message shouldBe "Ikke tilgang til å legge til deltakerliste i amt-arrangør"
        }

        @Test
        fun `leggTilDeltakerlisteForKoordinator - kaster RuntimeException ved 500`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.leggTilDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)
            }.message shouldBe "Kunne ikke legge til deltakerliste $deltakerlisteId i amt-arrangør."
        }
    }

    @Nested
    inner class FjernDeltakerlisteForKoordinatorTests {
        @Test
        fun `fjernDeltakerlisteForKoordinator - sender DELETE og returnerer ved suksess`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andRespond(withSuccess())

            sut.fjernDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)

            server.verify()
        }

        @Test
        fun `fjernDeltakerlisteForKoordinator - kaster UnauthorizedException ved 401`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<UnauthorizedException> {
                sut.fjernDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)
            }.message shouldBe "Ikke tilgang til å fjerne deltakerliste i amt-arrangør"
        }

        @Test
        fun `fjernDeltakerlisteForKoordinator - kaster RuntimeException ved 500`() {
            val ansattId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val deltakerlisteId = UUID.randomUUID()

            server
                .expect(requestTo("/api/ansatt/koordinator/$arrangorId/$deltakerlisteId"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.fjernDeltakerlisteForKoordinator(ansattId, deltakerlisteId, arrangorId)
            }.message shouldBe "Kunne ikke fjerne deltakerliste $deltakerlisteId i amt-arrangør."
        }
    }

    @Nested
    inner class OppdaterDeltakerlisteForKoordinatorTests {
        @Test
        fun `oppdaterVeilederForDeltaker - sender POST med body og returnerer ved suksess`() {
            val deltakerId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val ansattId = UUID.randomUUID()
            val request = OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorId,
                veilederSomLeggesTil = listOf(VeilederAnsatt(ansattId = ansattId, type = Veiledertype.VEILEDER)),
                veilederSomFjernes = listOf(),
            )

            server
                .expect(requestTo("/api/ansatt/veiledere/$deltakerId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess())

            sut.oppdaterVeilederForDeltaker(deltakerId, request)

            server.verify()
        }

        @Test
        fun `oppdaterVeilederForDeltaker - kaster UnauthorizedException ved 403`() {
            val deltakerId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val request = OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorId,
                veilederSomLeggesTil = listOf(),
                veilederSomFjernes = listOf(),
            )

            server
                .expect(requestTo("/api/ansatt/veiledere/$deltakerId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UnauthorizedException> {
                sut.oppdaterVeilederForDeltaker(deltakerId, request)
            }.message shouldBe "Ikke tilgang til å oppdatere veiledere i amt-arrangør"
        }

        @Test
        fun `oppdaterVeilederForDeltaker - kaster RuntimeException ved 500`() {
            val deltakerId = UUID.randomUUID()
            val arrangorId = UUID.randomUUID()
            val request = OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorId,
                veilederSomLeggesTil = listOf(),
                veilederSomFjernes = listOf(),
            )

            server
                .expect(requestTo("/api/ansatt/veiledere/$deltakerId"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ${ClientTestConfig.TOKEN}"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.oppdaterVeilederForDeltaker(deltakerId, request)
            }.message shouldBe "Kunne ikke oppdatere veiledere for deltaker $deltakerId i amt-arrangør."
        }
    }
}
