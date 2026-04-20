package no.nav.amt.deltaker.application.plugins

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SerializationTest {
    private data class DateTimeDto(
        val dateTime: LocalDateTime,
        val date: LocalDate,
    )

    @Test
    fun `Ktor serialiserer datoer som ISO-8601 strenger`() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/test") {
                    call.respond(
                        DateTimeDto(
                            dateTime = LocalDateTime.of(2026, 11, 23, 12, 34, 56),
                            date = LocalDate.of(2026, 11, 23),
                        ),
                    )
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldContain ContentType.Application.Json.toString()

        val body = response.bodyAsText()
        body shouldContain "\"dateTime\":\"2026-11-23T12:34:56\""
        body shouldContain "\"date\":\"2026-11-23\""
    }
}
