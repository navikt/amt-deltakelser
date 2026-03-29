package no.nav.amt.deltaker.bff

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.RouteTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest : RouteTestBase() {
    @Test
    fun `liveness skal returnere OK`() = runTest {
        withTestApplicationContext { httpClient ->
            httpClient.get("/internal/health/liveness").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals("I'm alive!", bodyAsText())
            }
        }
    }
}
