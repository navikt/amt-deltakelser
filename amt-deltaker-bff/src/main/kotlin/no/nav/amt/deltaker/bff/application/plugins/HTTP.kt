package no.nav.amt.deltaker.bff.application.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    install(CORS) {
        allowHost("127.0.0.1:3003")  // Tiltakskoordinators flate
        allowHost("127.0.0.1:3004")  // Nav-veileders flate
        allowHost("127.0.0.1:3005")  // Innbyggers flate

        allowCredentials = true

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeadersPrefixed("nav-")
    }
}
