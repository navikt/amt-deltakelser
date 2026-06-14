package no.nav.tiltaksarrangor.consumer.jobs.leaderelection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.InetAddress

/**
 * Tester [LeaderElection] med Spring REST Client.
 * Bruker @RestClientTest for å teste kun REST-laget uten full SpringBootTest-kontekst.
 */
@RestClientTest(LeaderElection::class)
@TestPropertySource(
    properties = [
        "elector.path=http://elector-service",
    ],
)
class LeaderElectionTest(
    @Autowired private val server: MockRestServiceServer,
    @Autowired private val sut: LeaderElection,
) {
    private val currentHostname = InetAddress.getLocalHost().hostName

    @Nested
    inner class WithElectorServiceTests {
        @Test
        fun `isLeader - returnerer true når egen hostname er leader`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """{"name": "$currentHostname"}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.isLeader()

            result shouldBe true
            server.verify()
        }

        @Test
        fun `isLeader - returnerer false når hostname ikke er leader`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """{"name": "other-pod-name"}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.isLeader()

            result shouldBe false
            server.verify()
        }

        @Test
        fun `isLeader - kaster exception når response body er null`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

            val exception = shouldThrow<RuntimeException> {
                sut.isLeader()
            }

            exception.message shouldBe "Response body is required"

            server.verify()
        }

        @Test
        fun `isLeader - kaster RuntimeException ved 500`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RuntimeException> {
                sut.isLeader()
            }

            server.verify()
        }

        @Test
        fun `isLeader - kaster RuntimeException ved 404`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            shouldThrow<RuntimeException> {
                sut.isLeader()
            }

            server.verify()
        }

        @Test
        fun `isLeader - kaster RuntimeException ved 403 Forbidden`() {
            server
                .expect(requestTo("http://elector-service"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<RuntimeException> {
                sut.isLeader()
            }

            server.verify()
        }
    }
}

/**
 * Tester [LeaderElection] når electorPath er satt til "dont_look_for_leader".
 * I dette tilfellet skal isLeader() alltid returnere true uten å gjøre HTTP-kall.
 */
@RestClientTest(LeaderElection::class)
@TestPropertySource(
    properties = [
        "elector.path=dont_look_for_leader",
    ],
)
class LeaderElectionWithoutElectorTest(
    @Autowired private val sut: LeaderElection,
) {
    @Test
    fun `isLeader - returnerer true når electorPath er dont_look_for_leader`() {
        val result = sut.isLeader()

        result shouldBe true
    }
}
