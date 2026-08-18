package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.ExternalServiceNonRetryableException
import no.nav.amt.lib.spring.boot.client.ExternalServiceRetryableException
import no.nav.amt.person.service.clients.AKTIVITET_ARENA_ACL_CLIENT_ID
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException
import java.util.UUID

@RestClientTest(AktivitetArenaAclClient::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AktivitetArenaAclClientTest(
    private val sut: AktivitetArenaAclClient,
) : RestClientTestBase(AKTIVITET_ARENA_ACL_CLIENT_ID) {
    @Test
    fun `getAktivitetIdForArenaId - returnerer id om eksisterer`() {
        val aktivitetId = UUID.randomUUID()
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer aktivitet-arena-acl-token"))
            .andRespond(withSuccess(""""$aktivitetId"""", MediaType.APPLICATION_JSON))

        val id = sut.getAktivitetIdForArenaId(1L)

        id shouldBe aktivitetId
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster exception ved 404`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getAktivitetIdForArenaId(1L)
        }
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster exception ved 401`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getAktivitetIdForArenaId(1L)
        }
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster exception ved 403`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getAktivitetIdForArenaId(1L)
        }
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster retryable exception ved 500`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.getAktivitetIdForArenaId(1L)
        }

        thrown.message shouldBe "aktivitet-arena-acl: kunne ikke hente aktivitetId for Arena-ID 1. Status=500"
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster retryable exception ved transportfeil`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(IOException("boom")))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.getAktivitetIdForArenaId(1L)
        }

        thrown.message shouldBe "aktivitet-arena-acl: kunne ikke hente aktivitetId for Arena-ID 1"
    }
}
