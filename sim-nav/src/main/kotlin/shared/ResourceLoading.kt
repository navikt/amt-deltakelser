package shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

inline fun <reified T> loadJsonResource(
    objectMapper: ObjectMapper,
    resourcePath: String,
): T {
    val stream = object {}.javaClass.getResourceAsStream(resourcePath)
        ?: throw IllegalStateException("Missing resource: $resourcePath")

    return stream.use { objectMapper.readValue(it) }
}

