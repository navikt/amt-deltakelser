package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.config.ClientConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@RestClientTest(components = [AktivitetArenaAclClient::class, ClientConfig::class])
@Import(OAuth2ClientTestConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AktivitetArenaAclClientTest(
    private val sut: AktivitetArenaAclClient,
) : RestClientTestBase("aktivitet-arena-acl") {
    @Test
    fun `getAktivitetIdForArenaId - returnerer id om eksisterer`() {
        val aktivitetId = UUID.randomUUID()
        server
            .expect(requestTo("/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(""""$aktivitetId"""", MediaType.APPLICATION_JSON))

        val id = sut.getAktivitetIdForArenaId(1L)

        id shouldBe aktivitetId
    }

    @Test
    fun `getAktivitetIdForArenaId - kaster exception ved 404`() {
        server
            .expect(requestTo("/api/translation/arenaid"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        shouldThrow<RuntimeException> {
            sut.getAktivitetIdForArenaId(1L)
        }
    }
}
