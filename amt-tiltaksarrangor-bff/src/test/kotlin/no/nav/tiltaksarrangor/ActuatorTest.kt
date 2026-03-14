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
) : IntegrationTest() {
	@ParameterizedTest(name = "{0} probe skal returnere OK og status = UP")
	@ValueSource(strings = ["liveness", "readiness"])
	fun probe_skal_returnere_OK_og_status_UP(probeName: String) {
		val uri =
			UriComponentsBuilder
				.fromUriString("http://localhost:{port}/internal/health/{probeName}")
				.buildAndExpand(managementPort, probeName)
				.toUri()

		val response = restTemplate.getForEntity<String>(uri)

		assertSoftly(response) {
			statusCode shouldBe HttpStatus.OK
			body shouldBe "{\"status\":\"UP\"}"
		}
	}

	@Test
	fun `Prometheus-endepunktet skal returnere OK`() {
		val uri =
			UriComponentsBuilder
				.fromUriString("http://localhost:{port}/internal/prometheus")
				.buildAndExpand(managementPort)
				.toUri()

		val response = restTemplate.getForEntity<String>(uri)

		response.statusCode shouldBe HttpStatus.OK
	}

	@Test
	fun `Metrics-endepunktet skal returnere NOT_FOUND`() {
		val uri =
			UriComponentsBuilder
				.fromUriString("http://localhost:{port}/internal/metrics")
				.buildAndExpand(managementPort)
				.toUri()

		val response = restTemplate.getForEntity<String>(uri)

		// GlobalExceptionHandler er satt opp til å gi INTERNAL_SERVER_ERROR for NOT_FOUND
		response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
	}
}
