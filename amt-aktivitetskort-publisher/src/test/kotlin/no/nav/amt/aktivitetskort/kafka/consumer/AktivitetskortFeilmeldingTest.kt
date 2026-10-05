package no.nav.amt.aktivitetskort.kafka.consumer

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.kafka.consumer.dto.AktivitetskortFeilmelding
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Year
import java.time.ZonedDateTime

@JsonTest
class AktivitetskortFeilmeldingTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `skal deserialisere JSON til AktivitetskortFeilmelding`() {
        val json =
            """
            {
              "key": "123e4567-e89b-12d3-a456-426614174000",
              "source": "some-source",
              "timestamp": "${Year.now()}-01-02T12:34:56+01:00",
              "failingMessage": "original message",
              "errorMessage": "noe gikk galt",
              "errorType": "TECHNICAL"
            }
            """.trimIndent()

        val deserialized = objectMapper.readValue<AktivitetskortFeilmelding>(json)

        deserialized.timestamp shouldBe ZonedDateTime.parse("${Year.now()}-01-02T11:34:56Z")
    }
}
