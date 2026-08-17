package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.utils.toSystemZoneLocalDateTime
import no.nav.amt.lib.spring.boot.client.ExternalServiceNonRetryableException
import no.nav.amt.lib.spring.boot.client.ExternalServiceRetryableException
import no.nav.amt.person.service.clients.VEILARBOPPFOLGING_CLIENT_ID
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@RestClientTest(VeilarboppfolgingClient::class)
class VeilarboppfolgingClientTest(
    @Autowired private val sut: VeilarboppfolgingClient,
) : RestClientTestBase(VEILARBOPPFOLGING_CLIENT_ID) {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `hentOppfolgingperiode - returnerer gyldig oppfolgingsperiode`(useEndDate: Boolean) {
        val uuid = UUID.randomUUID()
        val startDatoUtc = ZonedDateTime.now(ZoneOffset.UTC)
        val sluttDatoUtc = if (useEndDate) startDatoUtc.plusDays(1) else null

        val responseJson =
            """
            {
                "uuid":"$uuid",
                "startDato":"$startDatoUtc",
                "sluttDato":${if (useEndDate) "\"$sluttDatoUtc\"" else "null"}
            }
            """.trimIndent()

        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer veilarboppfolging-token"))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val oppfolgingsperiode = sut.hentOppfolgingperiode("123456789")

        assertSoftly(oppfolgingsperiode.shouldNotBeNull()) {
            id shouldBe uuid
            startDato shouldBe startDatoUtc.toSystemZoneLocalDateTime()

            if (useEndDate) {
                sluttDato shouldBe sluttDatoUtc?.toSystemZoneLocalDateTime()
            } else {
                sluttDato.shouldBeNull()
            }
        }
    }

    @Test
    fun `hentOppfolgingperiode - returnerer null ved 204 No Content`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent())

        val result = sut.hentOppfolgingperiode("12345678910")

        result.shouldBeNull()
    }

    @Test
    fun `hentOppfolgingperiode - kaster feil ved 500 status`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "veilarboppfolging: kunne ikke hente oppfølgingsperiode. Status=500"
    }

    @Test
    fun `hentOppfolgingperiode - kaster ikke-retrybar feil ved 404 status`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val thrown = shouldThrow<ExternalServiceNonRetryableException> {
            sut.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "veilarboppfolging: kunne ikke hente oppfølgingsperiode. Status=404"
    }

    @Test
    fun `hentOppfolgingperiode - kaster ikke-retrybar feil ved 401 status`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val thrown = shouldThrow<ExternalServiceNonRetryableException> {
            sut.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "veilarboppfolging: kunne ikke hente oppfølgingsperiode. Status=401"
    }

    @Test
    fun `hentOppfolgingperiode - kaster ikke-retrybar feil ved 403 status`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val thrown = shouldThrow<ExternalServiceNonRetryableException> {
            sut.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "veilarboppfolging: kunne ikke hente oppfølgingsperiode. Status=403"
    }

    @Test
    fun `hentOppfolgingperiode - kaster retryable feil ved transportfeil`() {
        server
            .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(IOException("boom")))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "veilarboppfolging: kunne ikke hente oppfølgingsperiode"
    }
}
