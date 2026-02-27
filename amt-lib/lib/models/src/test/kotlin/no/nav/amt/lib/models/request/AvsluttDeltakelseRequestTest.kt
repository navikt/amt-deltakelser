package no.nav.amt.lib.models.request

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AvsluttDeltakelseRequestTest {
    @Test
    fun `AvsluttDeltakelseRequest skal serialisere og deserialisere korrekt`() {
        val request = AvsluttDeltakelseRequest(
            endretAv = "test",
            endretAvEnhet = "test-enhet",
            forslagId = null,
            sluttdato = LocalDate.now(),
            aarsak = null,
            begrunnelse = null,
            harFullfort = true
        )

        val json = objectMapper.writeValueAsString(request)

        json shouldContain
            """
            "type" : "AvsluttDeltakelseRequest"
            """.trimIndent()

        val deserialized = objectMapper.readValue<AvsluttDeltakelseRequest>(json)
        deserialized shouldBe request
    }
}
