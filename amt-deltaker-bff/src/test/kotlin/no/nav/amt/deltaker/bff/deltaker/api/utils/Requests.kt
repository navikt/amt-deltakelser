package no.nav.amt.deltaker.bff.deltaker.api.utils

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.amt.deltaker.bff.utils.generateJWT
import no.nav.amt.deltaker.bff.utils.generateSystemJWT
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

internal fun HttpRequestBuilder.createPostRequest(body: Any) {
    bearerAuth(
        generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
        ),
    )
    header("aktiv-enhet", "0101")
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}

internal fun HttpRequestBuilder.noBodyRequest() {
    bearerAuth(
        generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
        ),
    )
    header("aktiv-enhet", "0101")
}

internal fun HttpRequestBuilder.createPostTiltakskoordinatorRequest(body: Any) {
    bearerAuth(
        generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
            groups = listOf(UUID(0L, 0L).toString()),
        ),
    )
    header("aktiv-enhet", "0101")
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}

internal fun HttpRequestBuilder.noBodyTiltakskoordinatorRequest() {
    bearerAuth(
        generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
            groups = listOf(UUID(0L, 0L).toString()),
        ),
    )
    header("aktiv-enhet", "0101")
}

internal fun HttpRequestBuilder.systemPostRequest(body: Any) {
    bearerAuth(
        generateSystemJWT(
            consumerClientId = "tiltakspenger-tiltak",
            audience = "deltaker-bff",
        ),
    )
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}
