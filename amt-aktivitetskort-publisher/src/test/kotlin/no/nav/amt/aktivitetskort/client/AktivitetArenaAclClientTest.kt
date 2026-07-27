package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@RestClientTest(AktivitetArenaAclClient::class)
@TestPropertySource(properties = ["aktivitet.arena-acl.url=http://aktivitet-arena-acl"])
class AktivitetArenaAclClientTest(
    private val sut: AktivitetArenaAclClient,
) : RestClientTestBase() {
    @Test
    fun `getAktivitetIdForArenaId - returnerer id om eksisterer`() {
        val aktivitetId = UUID.randomUUID()
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withSuccess(""""$aktivitetId"""", MediaType.APPLICATION_JSON))

        val id = sut.getAktivitetIdForArenaId(1L)

        id shouldBe aktivitetId
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster exception ved 404`() {
        server
            .expect(requestTo("http://aktivitet-arena-acl/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        shouldThrow<RuntimeException> {
            sut.getAktivitetIdForArenaId(1L)
        }
    }
}
