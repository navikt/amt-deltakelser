package no.nav.tiltaksarrangor.mock

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.amtperson.NavAnsattResponse
import no.nav.tiltaksarrangor.client.amtperson.NavEnhetDto
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class MockAmtPersonHttpServer : MockHttpServer(name = "amt-person-server") {
    init {
        // Fallback handlers for enhet and ansatt requests from leftover Kafka messages
        // or unregistered UUIDs. Specific handlers registered via addEnhetResponse/addAnsattResponse
        // take precedence. Fallback handlers survive resetHttpServer().
        addFallbackHandler(
            { req -> req.requestUrl?.encodedPath?.startsWith("/api/nav-enhet/") == true },
            { req ->
                val id = req.requestUrl?.pathSegments?.last() ?: UUID.randomUUID().toString()
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(NavEnhetDto(UUID.fromString(id), "0000", "Ukjent enhet")))
            },
        )
        addFallbackHandler(
            { req -> req.requestUrl?.encodedPath?.startsWith("/api/nav-ansatt/") == true },
            { req ->
                val id = req.requestUrl?.pathSegments?.last() ?: UUID.randomUUID().toString()
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        objectMapper.writeValueAsString(
                            NavAnsattResponse(
                                id = UUID.fromString(id),
                                navIdent = "X000000",
                                navn = "Ukjent ansatt",
                                epost = null,
                                telefon = null,
                            ),
                        ),
                    )
            },
        )
        addFallbackHandler(
            { req ->
                req.requestUrl?.encodedPath == "/api/nav-bruker/kontaktinformasjon" &&
                    req.method == "POST"
            },
            { req ->
                // Parse personidenter from request body and return empty kontaktinformasjon for each
                val body = req.getBodyAsString()
                val personidenter: Set<String> = objectMapper.readValue(body)
                val response = personidenter.associateWith {
                    Kontaktinformasjon(epost = null, telefonnummer = null)
                }
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(response))
            },
        )
    }

    fun addEnhetResponse(id: UUID = UUID.randomUUID()) {
        val enhetResponse = NavEnhetDto(
            id = id,
            enhetId = "EnhetId",
            navn = "Nav Oslo",
        )
        addResponseHandler(
            path = "/api/nav-enhet/$id",
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(enhetResponse)),
        )
    }

    fun addAnsattResponse(id: UUID = UUID.randomUUID()) {
        val ansattResponse = NavAnsattResponse(
            id = id,
            navIdent = "NAVident",
            navn = "Navn Navnsen",
            epost = "navn.navsen@test.no",
            telefon = "123",
        )
        addResponseHandler(
            path = "/api/nav-ansatt/$id",
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(ansattResponse)),
        )
    }

    fun addKontaktinformasjonResponse(
        personident: String,
        epost: String = "foo@bar.baz",
        telefonnnummer: String = "12345678",
    ) {
        val kontaktinformasjon = mapOf(
            personident to Kontaktinformasjon(
                epost = epost,
                telefonnummer = telefonnnummer,
            ),
        )

        val requestPredicate = { req: RecordedRequest ->
            req.requestUrl?.encodedPath == "/api/nav-bruker/kontaktinformasjon" &&
                req.method == "POST" &&
                try {
                    val body = req.getBodyAsString()
                    val personidenter: Set<String> = objectMapper.readValue(body)
                    personidenter == setOf(personident)
                } catch (_: Exception) {
                    false
                }
        }

        addResponseHandler(
            requestPredicate,
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(kontaktinformasjon)),
        )
    }
}
