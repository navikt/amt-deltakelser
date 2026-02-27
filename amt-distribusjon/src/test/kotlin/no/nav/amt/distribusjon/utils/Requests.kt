package no.nav.amt.distribusjon.utils

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.amt.distribusjon.application.plugins.objectMapper

internal fun HttpRequestBuilder.postRequest(body: Any) {
    header(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "amt-distribusjon",
            )
        }",
    )
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}
