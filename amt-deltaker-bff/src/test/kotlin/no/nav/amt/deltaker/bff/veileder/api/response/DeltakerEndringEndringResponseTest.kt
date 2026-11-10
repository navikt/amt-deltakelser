package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.deltaker.bff.commonresponse.PrisinformasjonResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.time.LocalDate

class DeltakerEndringEndringResponseTest {
    @Test
    fun `skal beholde arsaksfelter og typenavn for alle berorte endringer`() {
        val sluttdato = LocalDate.of(2026, 11, 10)
        val arsaker = listOf(
            null,
            DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.SYK),
            DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET),
            DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "Beskrivelse"),
        )

        arsaker.forEach { aarsak ->
            val endringer = listOf(
                DeltakerEndring.Endring.AvsluttDeltakelse(aarsak, sluttdato, null, true) to "AvsluttDeltakelse",
                DeltakerEndring.Endring.EndreAvslutning(aarsak, null, begrunnelse = null) to "EndreAvslutning",
            ) + if (aarsak != null) {
                listOf(
                    DeltakerEndring.Endring.IkkeAktuell(aarsak, null) to "IkkeAktuell",
                    DeltakerEndring.Endring.EndreSluttarsak(aarsak, null) to "EndreSluttarsak",
                    DeltakerEndring.Endring.AvbrytDeltakelse(aarsak, sluttdato, null) to "AvsluttDeltakelse",
                )
            } else {
                emptyList()
            }

            endringer.forEach { (model, typenavn) ->
                val response = DeltakerEndringEndringResponse.fromModel(model, null)
                val json = objectMapper.readTree(objectMapper.writeValueAsString(response))
                val forventet = aarsak?.let { mapOf("type" to it.type.name, "beskrivelse" to it.beskrivelse) }

                json["type"] shouldBe objectMapper.valueToTree<JsonNode>(typenavn)
                json["aarsak"] shouldBe objectMapper.valueToTree<JsonNode>(forventet)
            }
        }
    }

    @Test
    fun `endrePrisinfo - anskaffelse - mappes korrekt`() {
        // Arrange
        val prisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000)
        val endring = DeltakerEndring.Endring.EndrePrisinfo(
            prisinfo = prisinfo,
            begrunnelse = "Begrunnelse",
            status = DeltakerEndring.Endring.EndrePrisinfo.Status.SENDT_TIL_GODKJENNING,
        )

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response.prisinfo.shouldBeInstanceOf<PrisinformasjonResponse.Anskaffelse>()
    }

    @Test
    fun `endrePrisinfo - tilskudd - mappes korrekt`() {
        // Arrange
        val tilskuddInfo = PrisinformasjonDto.Tilskudd.TilskuddInfo(
            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
            pris = 3000,
        )
        val prisinfo = PrisinformasjonDto.Tilskudd(
            tilskudd = listOf(tilskuddInfo),
            tilleggsopplysninger = "Noen opplysninger",
        )
        val endring = DeltakerEndring.Endring.EndrePrisinfo(
            prisinfo = prisinfo,
            begrunnelse = "Begrunnelse",
            status = DeltakerEndring.Endring.EndrePrisinfo.Status.SENDT_TIL_GODKJENNING,
        )

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response
            .prisinfo
            .shouldBeInstanceOf<PrisinformasjonResponse.Tilskudd>()
    }

    @Test
    fun `endrePrisinfo - ingen kostnader - mappes korrekt`() {
        // Arrange
        val prisinfo = PrisinformasjonDto.IngenKostnader(
            tilleggsopplysninger = "",
        )
        val endring = DeltakerEndring.Endring.EndrePrisinfo(
            prisinfo = prisinfo,
            begrunnelse = "Begrunnelse",
            status = DeltakerEndring.Endring.EndrePrisinfo.Status.SENDT_TIL_GODKJENNING,
        )

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response
            .prisinfo
            .shouldBeInstanceOf<PrisinformasjonResponse.IngenKostnader>()
    }
}
