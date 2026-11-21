package no.nav.tiltaksarrangor

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class JsonClasspathTest {
    @Test
    fun `JSON-klassene har bare én implementasjon`() {
        listOf("org/json/JSONObject.class", "org/json/JSONArray.class").forEach { resource ->
            val locations = javaClass.classLoader
                .getResources(resource)
                .asSequence()
                .toList()

            locations shouldHaveSize 1
            locations.single().toString() shouldNotContain "android-json"
        }
    }
}
