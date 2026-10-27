package no.nav.amt.internapi.hendelse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

class HendelseTypeTest {
    private fun endrePrisinfoEndring(
        status: DeltakerEndring.Endring.EndrePrisinfo.Status? = DeltakerEndring.Endring.EndrePrisinfo.Status.ENDRET_DIREKTE,
        prisinformasjonId: UUID? = null,
        begrunnelse: String? = null,
    ) = DeltakerEndring(
        id = UUID.randomUUID(),
        deltakerId = UUID.randomUUID(),
        endring = DeltakerEndring.Endring.EndrePrisinfo(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
            ),
            begrunnelse = begrunnelse,
            prisinformasjonId = prisinformasjonId,
            status = status,
        ),
        endretAv = UUID.randomUUID(),
        endretAvEnhet = UUID.randomUUID(),
        endret = LocalDateTime.now(),
        forslag = null,
    )

    @Test
    fun `toHendelseEndring - prisinfo, status SOKT_INN - kreverGodkjenning false`() {
        val hendelseEndring = endrePrisinfoEndring().toHendelseEndring(deltakerStatus = DeltakerStatus.Type.SOKT_INN)

        hendelseEndring.shouldBeInstanceOf<HendelseType.EnkeltplassEndrePrisinfo>().kreverGodkjenning shouldBe false
    }

    @Test
    fun `toHendelseEndring - prisinfo - mapper begrunnelse`() {
        val hendelseEndring = endrePrisinfoEndring(
            begrunnelse = "Prisendringen er foreslått",
        ).toHendelseEndring(deltakerStatus = DeltakerStatus.Type.SOKT_INN)
            .shouldBeInstanceOf<HendelseType.EnkeltplassEndrePrisinfo>()

        hendelseEndring.begrunnelse shouldBe "Prisendringen er foreslått"
    }

    @Test
    fun `deserialiserer gammel prisinfo-hendelse uten begrunnelse`() {
        val json =
            """
            {
              "type": "EnkeltplassEndrePrisinfo",
              "kreverGodkjenning": false,
              "prisinfo": {
                "type": "IngenKostnader",
                "tilleggsopplysninger": ""
              }
            }
            """.trimIndent()

        val hendelseType = objectMapper
            .readValue<HendelseType>(json)
            .shouldBeInstanceOf<HendelseType.EnkeltplassEndrePrisinfo>()

        hendelseType.begrunnelse shouldBe null
    }

    @Test
    fun `toHendelseEndring - prisinfo, status etter soknad (DELTAR) - kreverGodkjenning true`() {
        val hendelseEndring = endrePrisinfoEndring()
            .toHendelseEndring(deltakerStatus = DeltakerStatus.Type.DELTAR)

        hendelseEndring.shouldBeInstanceOf<HendelseType.EnkeltplassEndrePrisinfo>().kreverGodkjenning shouldBe true
    }

    @Test
    fun `toHendelseEndring - prisinfo, status VENTER_PA_OPPSTART - kreverGodkjenning true`() {
        val hendelseEndring = endrePrisinfoEndring().toHendelseEndring(deltakerStatus = DeltakerStatus.Type.VENTER_PA_OPPSTART)

        hendelseEndring.shouldBeInstanceOf<HendelseType.EnkeltplassEndrePrisinfo>().kreverGodkjenning shouldBe true
    }

    @Test
    fun `toHendelseEndring - prisinfo, status TILBAKEKALT - mapper til tilbakekall uavhengig av deltakerstatus`() {
        val hendelseEndring = endrePrisinfoEndring(
            status = DeltakerEndring.Endring.EndrePrisinfo.Status.TILBAKEKALT,
            prisinformasjonId = UUID.randomUUID(),
        ).toHendelseEndring(deltakerStatus = DeltakerStatus.Type.SOKT_INN)

        hendelseEndring.shouldBeInstanceOf<HendelseType.EnkeltplassTilbakekallPrisendring>()
    }
}
