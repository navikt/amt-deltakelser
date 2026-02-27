package no.nav.amt.deltaker

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.amt.deltaker.utils.RouteTestBase
import org.junit.jupiter.api.Test

class ApplicationTest : RouteTestBase() {
    @Test
    fun testRoot() {
        withTestApplicationContext { client ->
            val response = client.get("/internal/health/liveness")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "I'm alive!"
        }
    }
}
