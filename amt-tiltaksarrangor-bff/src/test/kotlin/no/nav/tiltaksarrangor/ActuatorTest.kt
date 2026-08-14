package no.nav.tiltaksarrangor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.resttestclient.getForEntity
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

@AutoConfigureTestRestTemplate
class ActuatorTest(
    @LocalManagementPort private val managementPort: Int,
    private val restTemplate: TestRestTemplate,
) : IntegrationTestBase() {
    @ParameterizedTest(name = "{0} probe skal returnere OK og status = UP")
    @ValueSource(strings = ["liveness", "readiness"])
    fun probe_skal_returnere_OK_og_status_UP(probeName: String) {
        val uri = UriComponentsBuilder
            .fromUriString("http://localhost:{port}/actuator/health/{probeName}")
            .buildAndExpand(managementPort, probeName)
            .toUri()

        val response = restTemplate.getForEntity<String>(uri)

        response.statusCode shouldBe HttpStatus.OK

        val body = objectMapper.readTree(response.body)

        assertSoftly {
            body["status"].asString() shouldBe "UP"

            if (probeName == "readiness") {
                body["components"]["readinessState"]["status"].asString() shouldBe "UP"
                body["components"]["db"]["status"].asString() shouldBe "UP"
            } else {
                body["components"]["livenessState"]["status"].asString() shouldBe "UP"
            }
        }
    }

    @Test
    fun `Prometheus-endepunktet skal returnere OK`() {
        val uri = UriComponentsBuilder
            .fromUriString("http://localhost:{port}/actuator/prometheus")
            .buildAndExpand(managementPort)
            .toUri()

        val response = restTemplate.getForEntity<String>(uri)

        response.statusCode shouldBe HttpStatus.OK
    }
}
