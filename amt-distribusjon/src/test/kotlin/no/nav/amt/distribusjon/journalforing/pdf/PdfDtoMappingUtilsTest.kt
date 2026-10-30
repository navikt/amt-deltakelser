package no.nav.amt.distribusjon.journalforing.pdf

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.journalforing.pdf.EndringDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class PdfDtoMappingUtilsTest {
    @Test
    fun `mapper valgt kurstype fra innholdsendringen til endringsbrevet`() {
        // Arrange
        val hendelseType = HendelseType.EnkeltplassEndreOpplaringKategorisering(
            opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                        valg = mapOf(UUID.randomUUID() to "Norskopplæring"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            ),
            innholdFritekst = "Opplæring i norsk",
            pavirkerPris = true,
        )

        // Act
        val resultat = tilEndringDto(
            hendelseType = hendelseType,
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            erEnkeltplass = true,
            harFellesAvslutning = false,
        )

        // Assert
        resultat shouldBe EndringDto.EnkeltplassEndreOpplaringKategorisering(
            innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Kurstype("Norskopplæring"),
            innholdFritekst = "Opplæring i norsk",
            pavirkerPris = true,
            erEnkeltplassinnhold = true,
        )
    }

    @Test
    fun `mapper EnkeltplassEndrePrisinfo til EndrePrisinfo`() {
        val hendelseType = HendelseType.EnkeltplassEndrePrisinfo(
            kreverGodkjenning = false,
            prisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
            ),
            begrunnelse = "Prisendringen er foreslått",
        )

        val resultat = tilEndringDto(
            hendelseType = hendelseType,
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            erEnkeltplass = true,
            harFellesAvslutning = false,
        )

        resultat shouldBe EndringDto.EndrePrisinfo(
            tittel = "Pris og betalingsbetingelser er endret",
            prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader(),
            begrunnelseFraNav = "Prisendringen er foreslått",
        )
    }

    @Test
    fun `mapper EnkeltplassGodkjennPrisendring til GodkjennPrisendring`() {
        val hendelseType = HendelseType.EnkeltplassGodkjennPrisendring(
            prisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
            ),
            begrunnelse = "Prisendringen er godkjent",
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
            begrunnelseFraNav = "Prisendringen er godkjent",
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
