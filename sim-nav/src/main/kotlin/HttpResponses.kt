import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

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

