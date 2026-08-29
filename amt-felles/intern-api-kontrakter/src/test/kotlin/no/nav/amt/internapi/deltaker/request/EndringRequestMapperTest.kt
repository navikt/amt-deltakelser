package no.nav.amt.internapi.deltaker.request

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate
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
        )

        val endring = EndringRequestMapper.toEndring(request) as DeltakerEndring.Endring.ForlengDeltakelse

        endring.sluttdato shouldBe request.sluttdato
        endring.begrunnelse shouldBe request.begrunnelse
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
            pavirkerPris = false,
        )

        val endring = EndringRequestMapper.toEndring(
            request,
            opplaringKategoriseringValg = kategoriseringValg,
        ) as DeltakerEndring.Endring.EndreOpplaringKategorisering

        endring.opplaringKategoriseringValg shouldBe kategoriseringValg
        endring.beskrivelse shouldBe request.beskrivelse
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
