package no.nav.tiltaksarrangor

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.util.UriComponentsBuilder

@AutoConfigureRestTestClient
class ActuatorTest(
    @LocalManagementPort private val managementPort: Int,
    private val restTestClient: RestTestClient,
) : IntegrationTestBase() {
    @ParameterizedTest(name = "{0} probe skal returnere OK og status = UP")
    @ValueSource(strings = ["liveness", "readiness"])
    fun probe_skal_returnere_OK_og_status_UP(probeName: String) {
        val uri = UriComponentsBuilder
            .fromUriString("http://localhost:{port}/actuator/health/{probeName}")
            .buildAndExpand(managementPort, probeName)
            .toUri()

        val result = restTestClient
            .get()
            .uri(uri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()

        result.jsonPath("$.status").isEqualTo("UP")

        if (probeName == "readiness") {
            result
                .jsonPath("$.components.readinessState.status")
                .isEqualTo("UP")
                .jsonPath("$.components.db.status")
                .isEqualTo("UP")
        } else {
            result
                .jsonPath("$.components.livenessState.status")
                .isEqualTo("UP")
        }
    }

    @Test
    fun `Prometheus-endepunktet skal returnere OK`() {
        val uri = UriComponentsBuilder
            .fromUriString("http://localhost:{port}/actuator/prometheus")
            .buildAndExpand(managementPort)
            .toUri()

        restTestClient
            .get()
            .uri(uri)
            .exchange()
            .expectStatus()
            .isOk
    }
}
