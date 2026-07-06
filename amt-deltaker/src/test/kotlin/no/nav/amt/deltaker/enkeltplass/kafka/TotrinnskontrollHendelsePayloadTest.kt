package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class TotrinnskontrollHendelsePayloadTest {
    @Test
    fun `deserialiser - til behandling - parser navAnsatt-agent`() {
        val json =
            """
            {
              "id": "1bdbe918-45f3-429a-9d56-9c5055595779",
              "entityId": "5d6f9ae8-2d8d-4d22-9409-549ef96d6ea9",
              "type": "ENKELTPLASS_OKONOMI",
              "behandletAv": {
                "type": "NAV_ANSATT",
                "navIdent": "Z990079"
              },
              "behandletTidspunkt": "2026-05-12T12:11:55.466070Z",
              "besluttetAv": null,
              "besluttetTidspunkt": null,
              "status": "TIL_BEHANDLING",
              "aarsaker": [],
              "forklaring": null
            }
            """.trimIndent()

        val payload = objectMapper.readValue<TotrinnskontrollHendelsePayload>(json)

        payload.behandletAv shouldBe TotrinnskontrollHendelsePayload.TotrinnskontrollAgent.NavAnsatt(navIdent = "Z990079")
        payload.besluttetAv shouldBe null
    }

    @Test
    fun `deserialiser - godkjent - parser begge agenter`() {
        val json =
            """
            {
              "id": "1bdbe918-45f3-429a-9d56-9c5055595779",
              "entityId": "5d6f9ae8-2d8d-4d22-9409-549ef96d6ea9",
              "type": "ENKELTPLASS_OKONOMI",
              "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
              "behandletTidspunkt": "2026-05-12T12:11:55.466070Z",
              "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "L164122" },
              "besluttetTidspunkt": "2026-05-12T12:12:20.268043Z",
              "status": "GODKJENT",
              "aarsaker": [],
              "forklaring": null
            }
            """.trimIndent()

        val payload = objectMapper.readValue<TotrinnskontrollHendelsePayload>(json)

        payload.besluttetAv shouldBe TotrinnskontrollHendelsePayload.TotrinnskontrollAgent.NavAnsatt(navIdent = "L164122")
        payload.status shouldBe TotrinnskontrollHendelsePayload.Status.GODKJENT
    }
}
