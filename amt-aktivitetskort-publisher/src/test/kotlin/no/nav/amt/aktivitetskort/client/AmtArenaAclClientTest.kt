package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.lib.spring.boot.client.ExternalServiceNonRetryableException
import no.nav.amt.lib.spring.boot.client.ExternalServiceRetryableException
import no.nav.amt.person.service.clients.AMT_ARENA_ACL_CLIENT_ID
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException
import java.util.UUID

@RestClientTest(AmtArenaAclClient::class)
class AmtArenaAclClientTest(
    @Autowired private val sut: AmtArenaAclClient,
) : RestClientTestBase(AMT_ARENA_ACL_CLIENT_ID) {
    @Test
    fun `getArenaIdForAmtId - returnerer arenaid om eksisterer`() {
        val amtId = UUID.randomUUID()
        val arenaId = 1L
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arena-acl-token"))
            .andRespond(withSuccess("""{"arenaId": "$arenaId", "arenaHistId": null}""", MediaType.APPLICATION_JSON))

        val id = sut.getArenaIdForAmtId(amtId)

        id shouldBe arenaId
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception om arenahistid finnes`() {
        val amtId = UUID.randomUUID()
        val arenaHistId = 1L
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"arenaId": null, "arenaHistId": "$arenaHistId"}""", MediaType.APPLICATION_JSON))

        val thrown = shouldThrow<HistoriskArenaDeltakerException> {
            sut.getArenaIdForAmtId(amtId)
        }

        thrown.message shouldBe "amtId $amtId tilhører histdeltaker med id $arenaHistId"
    }

    @Test
    fun `getArenaIdForAmtId - returnerer null om ingen arenaid finnes`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"arenaId": null, "arenaHistId": null}""", MediaType.APPLICATION_JSON))

        sut.getArenaIdForAmtId(amtId) shouldBe null
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception ved 404`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getArenaIdForAmtId(amtId)
        }
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception ved 401`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getArenaIdForAmtId(amtId)
        }
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception ved 403`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        shouldThrow<ExternalServiceNonRetryableException> {
            sut.getArenaIdForAmtId(amtId)
        }
    }

    @Test
    fun `getArenaIdForAmtId - kaster retryable exception ved 500`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.getArenaIdForAmtId(amtId)
        }

        thrown.message shouldBe "amt-arena-acl: kunne ikke hente arenaId for amtId $amtId. Status=500"
    }

    @Test
    fun `getArenaIdForAmtId - kaster retryable exception ved transportfeil`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withException(IOException("boom")))

        val thrown = shouldThrow<ExternalServiceRetryableException> {
            sut.getArenaIdForAmtId(amtId)
        }

        thrown.message shouldBe "amt-arena-acl: kunne ikke hente arenaId for amtId $amtId"
    }
}
