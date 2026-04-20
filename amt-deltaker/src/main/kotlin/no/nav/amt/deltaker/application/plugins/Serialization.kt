package no.nav.amt.deltaker.application.plugins

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import no.nav.amt.lib.utils.applicationConfig

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson { applicationConfig() }
    }
}
