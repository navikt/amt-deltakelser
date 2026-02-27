package no.nav.amt.deltaker.bff.application.plugins

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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

/**
 * Inkluderer type informasjon som er definert av @JsonTypeInfo i lister og andre samlinger
 *
 * Hvis man bruker `writeValueAsString` på en `List<GeneriskType>` så vil den ikke inkludere `type`.
 */
inline fun <reified T> ObjectMapper.writePolymorphicListAsString(value: T): String =
    this.writerFor(object : TypeReference<T>() {}).writeValueAsString(value)
