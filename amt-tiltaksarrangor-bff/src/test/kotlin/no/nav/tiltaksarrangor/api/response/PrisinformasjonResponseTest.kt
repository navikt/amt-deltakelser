package no.nav.tiltaksarrangor.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.deltaker.response.TilskuddInfo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class PrisinformasjonResponseTest {
    @Test
    fun `tilskudd beholder felles tilskuddsinfo uten aa eksponere begrunnelse`() {
        listOf(null, "Tilleggsopplysninger").forEach { tilleggsopplysninger ->
            val tilskudd = PrisinformasjonDto.Tilskudd.Tilskuddstype.entries.map {
                PrisinformasjonDto.Tilskudd.TilskuddInfo(it, 3000)
            }
            val response = PrisinformasjonResponse.fromModel(
                PrisinformasjonDto.Tilskudd(tilskudd, tilleggsopplysninger, "Kun for veileder"),
            )

            response shouldBe PrisinformasjonResponse.Tilskudd(
                tilskudd = tilskudd.map(::TilskuddInfo),
                tilleggsopplysninger = tilleggsopplysninger,
            )
            objectMapper.valueToTree<JsonNode>(response) shouldBe objectMapper.valueToTree<JsonNode>(
                mapOf(
                    "type" to "Tilskudd",
                    "tilskudd" to tilskudd.map { mapOf("type" to it.type.name, "pris" to it.pris) },
                    "tilleggsopplysninger" to tilleggsopplysninger,
                ),
            )
        }
    }
}
