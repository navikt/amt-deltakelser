package http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun respondJson(call: ApplicationCall, status: HttpStatusCode, body: String) {
    call.respondText(
        text = body,
        contentType = ContentType.Application.Json,
        status = status,
    )
}

suspend fun respondEmpty(call: ApplicationCall, status: HttpStatusCode) {
    call.respondText(text = "", status = status)
}

suspend fun readRequestBody(call: ApplicationCall): String = call.receiveText()

suspend fun respondGzipJson(
    call: ApplicationCall,
    contentType: String,
    body: ByteArray,
) {
    call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
    call.respondBytes(
        bytes = body,
        contentType = ContentType.parse(contentType),
        status = HttpStatusCode.OK,
    )
}

