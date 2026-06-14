package no.nav.tiltaksarrangor.client.amtperson

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.ClientTestConfig
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@ActiveProfiles("test")
@RestClientTest(AmtPersonClient::class)
@Import(ClientTestConfig::class)
@TestPropertySource(
    properties = [
        "amt-person.url=http://amt-person-service",
    ],
)
class AmtPersonClientTest(
    @Autowired private val sut: AmtPersonClient,
    @Autowired private val server: MockRestServiceServer,
) {
    @Nested
    inner class HentEnhetTests {
        @Test
        fun `hentEnhet - returnerer NavEnhet ved suksess`() {
            val idInTest = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-enhet/$idInTest"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$idInTest",
                          "enhetId": "0315",
                          "navn": "NAV Grünerløkka"
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentEnhet(idInTest)
            assertSoftly(result) {
                id shouldBe idInTest
                enhetsnummer shouldBe "0315"
                navn shouldBe "NAV Grünerløkka"
            }
        }

        @Test
        fun `hentEnhet - kaster UnauthorizedException ved 403`() {
            val id = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-enhet/$id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UnauthorizedException> {
                sut.hentEnhet(id)
            }.message shouldBe "Ikke tilgang til å hente NAV-enhet fra amt-person-service"
        }

        @Test
        fun `hentEnhet - kaster RuntimeException ved 500`() {
            val id = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-enhet/$id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.hentEnhet(id)
            }.message shouldBe "Kunne ikke hente NAV-enhet fra amt-person-service"
        }
    }

    @Nested
    inner class HentNavAnsattTests {
        @Test
        fun `hentNavAnsatt - returnerer NavAnsattResponse ved suksess`() {
            val idInTest = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-ansatt/$idInTest"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$idInTest",
                          "navIdent": "Z123456",
                          "navn": "Veileder Vansen",
                          "epost": "veileder@nav.no",
                          "telefon": "12345678"
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentNavAnsatt(idInTest)
            assertSoftly(result) {
                id shouldBe idInTest
                navIdent shouldBe "Z123456"
                navn shouldBe "Veileder Vansen"
                epost shouldBe "veileder@nav.no"
                telefon shouldBe "12345678"
            }
        }

        @Test
        fun `hentNavAnsatt - returnerer NavAnsattResponse med null-felter`() {
            val idInTest = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-ansatt/$idInTest"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$idInTest",
                          "navIdent": "Z123456",
                          "navn": "Veileder Vansen",
                          "epost": null,
                          "telefon": null
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentNavAnsatt(idInTest)
            assertSoftly(result) {
                id shouldBe idInTest
                epost shouldBe null
                telefon shouldBe null
            }
        }

        @Test
        fun `hentNavAnsatt - kaster UnauthorizedException ved 403`() {
            val id = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-ansatt/$id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UnauthorizedException> {
                sut.hentNavAnsatt(id)
            }.message shouldBe "Ikke tilgang til å hente NAV-ansatt fra amt-person-service"
        }

        @Test
        fun `hentNavAnsatt - kaster RuntimeException ved 500`() {
            val id = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-person-service/api/nav-ansatt/$id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.hentNavAnsatt(id)
            }.message shouldBe "Kunne ikke hente NAV-ansatt fra amt-person-service"
        }
    }

    @Nested
    inner class HentOppdatertKontaktinfoTests {
        @Test
        fun `hentOppdatertKontaktinfo - enkelt personident - returnerer kontaktinformasjon`() {
            val personident = "12345678901"

            server
                .expect(requestTo("http://amt-person-service/api/nav-bruker/kontaktinformasjon"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""["$personident"]"""))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "$personident": {
                            "telefonnummer": "99887766",
                            "epost": "test@example.com"
                          }
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentOppdatertKontaktinfo(personident).shouldBeSuccess()

            result shouldBe Kontaktinformasjon(telefonnummer = "99887766", epost = "test@example.com")
        }

        @Test
        fun `hentOppdatertKontaktinfo - enkelt personident - returnerer feil når person ikke finnes i response`() {
            val personident = "12345678901"

            server
                .expect(requestTo("http://amt-person-service/api/nav-bruker/kontaktinformasjon"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """{}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentOppdatertKontaktinfo(personident)

            result.isFailure shouldBe true
            shouldThrow<NoSuchElementException> {
                result.getOrThrow()
            }.message shouldBe "Klarte ikke hente kontaktinformasjon for person med ident"
        }

        @Test
        fun `hentOppdatertKontaktinfo - flere personidenter - returnerer map`() {
            val personident1 = "12345678901"
            val personident2 = "98765432100"

            server
                .expect(requestTo("http://amt-person-service/api/nav-bruker/kontaktinformasjon"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "$personident1": {
                            "telefonnummer": "11111111",
                            "epost": "en@example.com"
                          },
                          "$personident2": {
                            "telefonnummer": null,
                            "epost": "to@example.com"
                          }
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentOppdatertKontaktinfo(setOf(personident1, personident2))

            val map = result.shouldBeSuccess()

            map[personident1] shouldBe Kontaktinformasjon(telefonnummer = "11111111", epost = "en@example.com")
            map[personident2] shouldBe Kontaktinformasjon(telefonnummer = null, epost = "to@example.com")
        }

        @Test
        fun `hentOppdatertKontaktinfo - returnerer failure ved 500`() {
            val personident = "12345678901"

            server
                .expect(requestTo("http://amt-person-service/api/nav-bruker/kontaktinformasjon"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            val result = sut.hentOppdatertKontaktinfo(personident)

            result.isFailure shouldBe true
        }
    }
}
