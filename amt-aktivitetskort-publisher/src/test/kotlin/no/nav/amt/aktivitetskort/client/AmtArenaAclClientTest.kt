package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
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

@RestClientTest(AmtArenaAclClient::class)
@TestPropertySource(properties = ["amt.arena-acl.url=http://arena-acl"])
class AmtArenaAclClientTest(
    private val sut: AmtArenaAclClient,
) : RestClientTestBase() {
    @Test
    fun `getArenaIdForAmtId - returnerer arenaid om eksisterer`() {
        val amtId = UUID.randomUUID()
        val arenaId = 1L
        server
            .expect(requestTo("http://arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withSuccess("""{"arenaId": "$arenaId", "arenaHistId": null}""", MediaType.APPLICATION_JSON))

        val id = sut.getArenaIdForAmtId(amtId)

        id shouldBe arenaId
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception om arenahistid finnes`() {
        val amtId = UUID.randomUUID()
        val arenaHistId = 1L
        server
            .expect(requestTo("http://arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
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
            .expect(requestTo("http://arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withSuccess("""{"arenaId": null, "arenaHistId": null}""", MediaType.APPLICATION_JSON))

        sut.getArenaIdForAmtId(amtId) shouldBe null
    }

    @Test
    fun `getArenaIdForAmtId - kaster exception ved 404`() {
        val amtId = UUID.randomUUID()
        server
            .expect(requestTo("http://arena-acl/api/v2/translation/$amtId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        shouldThrow<RuntimeException> {
            sut.getArenaIdForAmtId(amtId)
        }
    }
}
