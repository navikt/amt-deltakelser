package no.nav.amt.deltaker.bff

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        configureEnvForAuthentication()
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
            )
        }
        client.get("/internal/health/liveness").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("I'm alive!", bodyAsText())
        }
    }
}
