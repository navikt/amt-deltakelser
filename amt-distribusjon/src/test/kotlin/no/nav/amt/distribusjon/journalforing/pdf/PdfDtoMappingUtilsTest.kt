package no.nav.amt.distribusjon.journalforing.pdf

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.journalforing.pdf.EndringDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test

class PdfDtoMappingUtilsTest {
    @Test
    fun `mapper EnkeltplassGodkjennPrisendring til GodkjennPrisendring`() {
        val hendelseType = HendelseType.EnkeltplassGodkjennPrisendring(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
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
            prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader(),
        )
    }

    @Test
    fun `beholder tilleggsopplysninger for IngenKostnader i prisendring`() {
        val hendelseType = HendelseType.EnkeltplassGodkjennPrisendring(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "Detaljer om kostnadsfri opplæring",
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
            prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader(
                tilleggsopplysninger = "Detaljer om kostnadsfri opplæring",
            ),
        )
    }
}
