package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class EndringFraArrangorEndringResponseTest {
    @Test
    fun `skal mappe oppstartsdato og bevare polymorf JSON-kontrakt`() {
        val model = EndringFraArrangor.LeggTilOppstartsdato(
            startdato = LocalDate.of(2026, 10, 26),
            sluttdato = LocalDate.of(2026, 12, 31),
        )

        val response = EndringFraArrangorEndringResponse.fromModel(model)

        response shouldBe EndringFraArrangorEndringResponse.LeggTilOppstartsdato(model.startdato, model.sluttdato)
        val json = objectMapper.writeValueAsString(response)
        objectMapper.readTree(json) shouldBe objectMapper.readTree(
            """{"type": "LeggTilOppstartsdato", "startdato": "2026-10-26", "sluttdato": "2026-12-31"}""",
        )
        objectMapper.readValue<EndringFraArrangorEndringResponse>(json) shouldBe response
    }

    @Test
    fun `skal bevare null sluttdato i mapping og JSON`() {
        val model = EndringFraArrangor.LeggTilOppstartsdato(
            startdato = LocalDate.of(2026, 10, 26),
            sluttdato = null,
        )

        val response = EndringFraArrangorEndringResponse.fromModel(model)

        response shouldBe EndringFraArrangorEndringResponse.LeggTilOppstartsdato(model.startdato, null)
        val json = objectMapper.writeValueAsString(response)
        objectMapper.readTree(json) shouldBe objectMapper.readTree(
            """{"type": "LeggTilOppstartsdato", "startdato": "2026-10-26", "sluttdato": null}""",
        )
        objectMapper.readValue<EndringFraArrangorEndringResponse>(json) shouldBe response
    }
}
