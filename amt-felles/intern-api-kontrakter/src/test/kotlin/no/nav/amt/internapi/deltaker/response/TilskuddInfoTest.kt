package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class TilskuddInfoTest {
    @Test
    fun `alle tilskuddstyper beholder pris og JSON-felter`() {
        PrisinformasjonDto.Tilskudd.Tilskuddstype.entries.forEach { type ->
            val response = TilskuddInfo(PrisinformasjonDto.Tilskudd.TilskuddInfo(type, 3000))

            response shouldBe TilskuddInfo(type, 3000)
            objectMapper.valueToTree<JsonNode>(response) shouldBe objectMapper.valueToTree<JsonNode>(
                mapOf("type" to type.name, "pris" to 3000),
            )
        }
    }
}
