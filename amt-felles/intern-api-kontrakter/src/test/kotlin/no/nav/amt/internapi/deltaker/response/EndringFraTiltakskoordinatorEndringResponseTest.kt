package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class EndringFraTiltakskoordinatorEndringResponseTest {
    @Test
    fun `objektvariantene beholder mapping og typefelt`() {
        val varianter = listOf(
            Triple(
                EndringFraTiltakskoordinator.DelMedArrangor,
                EndringFraTiltakskoordinatorEndringResponse.DelMedArrangor,
                "DelMedArrangor",
            ),
            Triple(
                EndringFraTiltakskoordinator.SettPaaVenteliste,
                EndringFraTiltakskoordinatorEndringResponse.SettPaaVenteliste,
                "SettPaaVenteliste",
            ),
            Triple(
                EndringFraTiltakskoordinator.TildelPlass,
                EndringFraTiltakskoordinatorEndringResponse.TildelPlass,
                "TildelPlass",
            ),
        )

        varianter.forEach { (model, forventet, type) ->
            val response = EndringFraTiltakskoordinatorEndringResponse.fromModel(model)

            response shouldBe forventet
            objectMapper.valueToTree<JsonNode>(response) shouldBe
                objectMapper.valueToTree<JsonNode>(mapOf("type" to type))
        }
    }

    @Test
    fun `avslag beholder aarsak beskrivelse og nullable begrunnelse`() {
        EndringFraTiltakskoordinator.Avslag.Aarsak.Type.entries.forEach { type ->
            val beskrivelser = if (type == EndringFraTiltakskoordinator.Avslag.Aarsak.Type.ANNET) {
                listOf(null, "Beskrivelse")
            } else {
                listOf(null)
            }
            beskrivelser.forEach { beskrivelse ->
                listOf(null, "Begrunnelse").forEach { begrunnelse ->
                    val model = EndringFraTiltakskoordinator.Avslag(
                        aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(type, beskrivelse),
                        begrunnelse = begrunnelse,
                    )
                    val response = EndringFraTiltakskoordinatorEndringResponse.fromModel(model)

                    response shouldBe EndringFraTiltakskoordinatorEndringResponse.Avslag(
                        aarsak = EndringFraTiltakskoordinatorEndringResponse.Avslag.Aarsak(type, beskrivelse),
                        begrunnelse = begrunnelse,
                    )
                    objectMapper.valueToTree<JsonNode>(response) shouldBe objectMapper.valueToTree<JsonNode>(
                        mapOf(
                            "type" to "Avslag",
                            "aarsak" to mapOf("type" to type.name, "beskrivelse" to beskrivelse),
                            "begrunnelse" to begrunnelse,
                        ),
                    )
                }
            }
        }
    }
}
