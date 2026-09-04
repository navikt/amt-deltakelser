package no.nav.amt.distribusjon.journalforing.pdf

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.journalforing.pdf.EndringDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test

class PdfDtoMappingUtilsTest {
    @Test
    fun `mapper EnkeltplassGodkjennPrisendring til GodkjennPrisendring`() {
        val hendelseType = HendelseType.EnkeltplassGodkjennPrisendring(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            ),
        )

        val resultat = tilEndringDto(
            hendelseType = hendelseType,
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            erEnkeltplass = true,
            harFellesAvslutning = false,
        )

        resultat shouldBe EndringDto.GodkjennPrisendring(
            tittel = "Pris og betalingsbetingelser er endret",
            prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
        )
    }
}
