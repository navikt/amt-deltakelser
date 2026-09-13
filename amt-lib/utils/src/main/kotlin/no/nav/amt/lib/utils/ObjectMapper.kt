package no.nav.amt.lib.utils

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

val objectMapper: ObjectMapper = jacksonObjectMapper()

/**
 * Inkluderer type informasjon som er definert av @JsonTypeInfo i lister og andre samlinger
 *
 * Hvis man bruker `writeValueAsString` på en `List<GeneriskType>` så vil den ikke inkludere `type`.
 */
inline fun <reified T : Collection<*>> ObjectMapper.writePolymorphicCollectionAsString(value: T): String =
    this.writerFor(object : TypeReference<T>() {}).writeValueAsString(value)

@Deprecated(
    message = "Use writePolymorphicCollectionAsString for collections",
    replaceWith = ReplaceWith("this.writePolymorphicCollectionAsString(value)"),
)
inline fun <reified T> ObjectMapper.writePolymorphicListAsString(value: T): String =
    this.writerFor(object : TypeReference<T>() {}).writeValueAsString(value)
