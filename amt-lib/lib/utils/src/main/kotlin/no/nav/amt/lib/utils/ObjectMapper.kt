package no.nav.amt.lib.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun ObjectMapper.applicationConfig() {
    registerModule(JavaTimeModule())
    registerKotlinModule()
    enable(SerializationFeature.INDENT_OUTPUT)
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}

val objectMapper = jacksonObjectMapper().apply { applicationConfig() }
