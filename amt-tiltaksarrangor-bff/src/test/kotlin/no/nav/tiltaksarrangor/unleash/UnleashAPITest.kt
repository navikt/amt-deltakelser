package no.nav.tiltaksarrangor.unleash

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class UnleashAPITest(
    private val mockMvc: MockMvc,
) : IntegrationTestBase() {
    @Test
    fun `getFeaturetoggles - ikke autentisert - returnerer 401`() {
        mockMvc
            .get("/unleash/api/feature?feature=amt-tiltaksarrangor-flate.driftsmelding&amt-tiltaksarrangor-flate.eksponer-kurs")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `getFeaturetoggles - autentisert - returnerer toggles`() {
        val response = mockMvc
            .get("/unleash/api/feature?feature=amt-tiltaksarrangor-flate.driftsmelding&feature=amt-tiltaksarrangor-flate.eksponer-kurs") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = "12345678910")}")
            }.andExpect { status { isOk() } }
            .andReturn()
            .response
            .contentAsString

        val expectedJson =
            """
            {"amt-tiltaksarrangor-flate.driftsmelding":true,"amt-tiltaksarrangor-flate.eksponer-kurs":true}
            """.trimIndent()
        response shouldBe expectedJson
    }
}
