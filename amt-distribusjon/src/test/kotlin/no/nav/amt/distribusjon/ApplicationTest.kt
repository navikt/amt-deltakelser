package no.nav.amt.distribusjon

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest {
    @Test
    fun testRoot() = integrationTest { _, client ->
        client.get("/internal/health/liveness").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("I'm alive!", bodyAsText())
        }
    }
}
