package no.nav.amt.internapi.deltaker.request

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class EndringRequestMapperTest {
    @Test
    fun `forleng deltakelse - returnerer ForlengDeltakelse`() {
        val request = ForlengDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = "begrunnelse",
            pavirkerPris = false,
        )

        val endring = EndringRequestMapper.toEndring(request) as DeltakerEndring.Endring.ForlengDeltakelse

        endring.sluttdato shouldBe request.sluttdato
        endring.begrunnelse shouldBe request.begrunnelse
        endring.pavirkerPris shouldBe request.pavirkerPris
    }

    @Test
    fun `startdato - mapper pavirkerPris`() {
        val request = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusWeeks(2),
            begrunnelse = "begrunnelse",
            pavirkerPris = true,
        )

        val endring = EndringRequestMapper.toEndring(request) as DeltakerEndring.Endring.EndreStartdato

        endring.pavirkerPris shouldBe true
    }

    @Test
    fun `deltakelsesmengde - mapper pavirkerPris`() {
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = 3,
            begrunnelse = "begrunnelse",
            gyldigFra = LocalDate.now(),
            pavirkerPris = true,
        )

        val endring = EndringRequestMapper.toEndring(request) as DeltakerEndring.Endring.EndreDeltakelsesmengde

        endring.pavirkerPris shouldBe true
    }

    @Test
    fun `endret innhold - med tiltakstype - returnerer EndreInnhold`() {
        val tiltakstype = lagTiltakstype()
        val request = EndretInnholdRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            innholdselementer = emptyList(),
        )

        val endring = EndringRequestMapper.toEndring(request, tiltakstype = tiltakstype) as DeltakerEndring.Endring.EndreInnhold

        endring.ledetekst shouldBe tiltakstype.innhold?.ledetekst
    }

    @Test
    fun `endret innhold - uten tiltakstype - kaster IllegalArgumentException`() {
        val request = EndretInnholdRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            innholdselementer = emptyList(),
        )

        shouldThrow<IllegalArgumentException> {
            EndringRequestMapper.toEndring(request)
        }
    }

    @Test
    fun `endret opplæringskategorisering - med valg - returnerer EndreOpplaringKategorisering`() {
        val kategoriseringValg = lagOpplaringKategoriseringValg()
        val request = EndretOpplaringKategoriseringRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            beskrivelse = "begrunnelse",
            opplaringKategoriseringValg = setOf(
                OpplaringKategoriseringValgRequest(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valgteIder = setOf(UUID.randomUUID()),
                ),
            ),
            sertifiseringValg = emptySet(),
            pavirkerPris = true,
        )

        val endring = EndringRequestMapper.toEndring(
            request,
            opplaringKategoriseringValg = kategoriseringValg,
        ) as DeltakerEndring.Endring.EndreOpplaringKategorisering

        endring.opplaringKategoriseringValg shouldBe kategoriseringValg
        endring.beskrivelse shouldBe request.beskrivelse
        endring.pavirkerPris shouldBe true
    }

    @Test
    fun `endret opplæringskategorisering - uten valg - kaster IllegalArgumentException`() {
        val request = EndretOpplaringKategoriseringRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            beskrivelse = "begrunnelse",
            opplaringKategoriseringValg = emptySet(),
            sertifiseringValg = emptySet(),
            pavirkerPris = false,
        )

        shouldThrow<IllegalArgumentException> {
            EndringRequestMapper.toEndring(request)
        }
    }

    @Test
    fun `tilbakekalt prisinfo - mapper status og prisinformasjonId`() {
        val prisinformasjonId = UUID.randomUUID()
        val request = TilbakekaltPrisendringRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            prisinformasjonId = prisinformasjonId,
        )
        val prisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000)

        val endring = EndringRequestMapper.toEndring(
            request = request,
            prisinfo = prisinfo,
        ) as DeltakerEndring.Endring.EndrePrisinfo

        endring.prisinfo shouldBe prisinfo
        endring.status shouldBe DeltakerEndring.Endring.EndrePrisinfo.Status.TILBAKEKALT
        endring.prisinformasjonId shouldBe prisinformasjonId
    }

    @Test
    fun `tilbakekalt prisinfo - uten prisinfo - kaster IllegalArgumentException`() {
        val request = TilbakekaltPrisendringRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
        )

        shouldThrow<IllegalArgumentException> {
            EndringRequestMapper.toEndring(request)
        }
    }

    @Test
    fun `endret prisinfo - vedtak ikke fattet - mapper til ENDRET_DIREKTE`() {
        val endring = EndringRequestMapper.toEndring(
            request = lagEndretPrisinfoRequest(),
            vedtakFattet = null,
        ) as DeltakerEndring.Endring.EndrePrisinfo

        endring.status shouldBe DeltakerEndring.Endring.EndrePrisinfo.Status.ENDRET_DIREKTE
    }

    @Test
    fun `endret prisinfo - vedtak fattet - mapper til SENDT_TIL_GODKJENNING`() {
        val endring = EndringRequestMapper.toEndring(
            request = lagEndretPrisinfoRequest(),
            vedtakFattet = LocalDateTime.now(),
        ) as DeltakerEndring.Endring.EndrePrisinfo

        endring.status shouldBe DeltakerEndring.Endring.EndrePrisinfo.Status.SENDT_TIL_GODKJENNING
    }

    private fun lagEndretPrisinfoRequest() = EndretPrisinfoRequest(
        endretAv = randomNavIdent(),
        endretAvEnhet = randomEnhetsnummer(),
        prisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000),
        begrunnelse = "Begrunnelse",
    )

    private fun lagTiltakstype() = Tiltakstype(
        id = UUID.randomUUID(),
        navn = "Test tiltak",
        tiltakskode = Tiltakskode.OPPFOLGING,
        innsatsgrupper = setOf(Innsatsgruppe.STANDARD_INNSATS),
        innhold = DeltakerRegistreringInnhold(
            innholdselementer = listOf(Innholdselement("Tekst", "kode")),
            ledetekst = "Ledetekst",
        ),
    )

    private fun lagOpplaringKategoriseringValg() = OpplaringKategoriseringValg(
        valgteKategoriseringer = emptySet(),
        valgteSertifiseringer = emptySet<SertifiseringValg>(),
    )
}
