package no.nav.tiltaksarrangor.unleash

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders

class UnleashAPITest : IntegrationTest() {
	@Test
	fun `getFeaturetoggles - ikke autentisert - returnerer 401`() {
		val response =
			sendRequest(
				method = "GET",
				path = "/unleash/api/feature?feature=amt-tiltaksarrangor-flate.driftsmelding&amt-tiltaksarrangor-flate.eksponer-kurs",
			)

		response.code shouldBe 401
	}

	@Test
	fun `getFeaturetoggles - autentisert - returnerer toggles`() {
		val response =
			sendRequest(
				method = "GET",
				path = "/unleash/api/feature?feature=amt-tiltaksarrangor-flate.driftsmelding&feature=amt-tiltaksarrangor-flate.eksponer-kurs",
				headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = "12345678910")}"),
			)

		val expectedJson =
			"""
			{"amt-tiltaksarrangor-flate.driftsmelding":true,"amt-tiltaksarrangor-flate.eksponer-kurs":true}
			""".trimIndent()
		response.code shouldBe 200
		response.body.string() shouldBe expectedJson
	}
}
