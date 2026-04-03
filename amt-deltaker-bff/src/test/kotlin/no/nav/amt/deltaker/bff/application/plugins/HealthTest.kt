package no.nav.amt.deltaker.bff.application.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Test

class HealthTest : IntegrationTestBase() {
    @Test
    fun `liveness-endepunktet skal returnere 200 OK naar app er i live`() {
        withTestApplicationContext { httpClient ->
            val httpResponse: HttpResponse = httpClient.get("/internal/health/liveness")
            httpResponse.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `readiness-endepunktet skal returnere 503 ServiceUnavailable naar app ikke er klar`() {
        withTestApplicationContext(appIsReady = false) { httpClient ->
            val response = httpClient.get("/internal/health/readiness")
            response.status shouldBe HttpStatusCode.ServiceUnavailable
        }
    }

    @Test
    fun `readiness-endepunktet skal returnere 200 OK naar app er klar`() {
        withTestApplicationContext { httpClient ->
            val response = httpClient.get("/internal/health/readiness")
            response.status shouldBe HttpStatusCode.OK
        }
    }
}
