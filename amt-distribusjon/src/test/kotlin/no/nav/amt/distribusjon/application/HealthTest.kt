package no.nav.amt.distribusjon.application

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import no.nav.amt.distribusjon.integrationTest
import org.junit.jupiter.api.Test

class HealthTest {
    @Test
    fun `liveness-endepunktet skal returnere 200 OK naar app er i live`() = integrationTest { _, httpClient ->
        val httpResponse: HttpResponse = httpClient.get("/internal/health/liveness")
        httpResponse.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `readiness-endepunktet skal returnere 503 ServiceUnavailable naar app ikke er klar`() = integrationTest(false) { _, httpClient ->
        val response = httpClient.get("/internal/health/readiness")
        response.status shouldBe HttpStatusCode.ServiceUnavailable
    }

    @Test
    fun `readiness-endepunktet skal returnere 200 OK naar app er klar`() = integrationTest { _, httpClient ->
        val response = httpClient.get("/internal/health/readiness")
        response.status shouldBe HttpStatusCode.OK
    }
}
