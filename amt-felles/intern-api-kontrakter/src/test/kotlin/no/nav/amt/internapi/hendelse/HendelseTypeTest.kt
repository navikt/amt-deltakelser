package no.nav.amt.internapi.hendelse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class HendelseTypeTest {
    private fun endrePrisinfoEndring(
        status: DeltakerEndring.Endring.EndrePrisinfo.Status? = DeltakerEndring.Endring.EndrePrisinfo.Status.ENDRET_DIREKTE,
        prisinformasjonId: UUID? = null,
    ) = DeltakerEndring(
        id = UUID.randomUUID(),
        deltakerId = UUID.randomUUID(),
        endring = DeltakerEndring.Endring.EndrePrisinfo(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            ),
            begrunnelse = null,
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
    fun `toHendelseEndring - prisinfo, status etter soknad (DELTAR) - kreverGodkjenning true`() {
        val hendelseEndring = endrePrisinfoEndring().toHendelseEndring(deltakerStatus = DeltakerStatus.Type.DELTAR)

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
