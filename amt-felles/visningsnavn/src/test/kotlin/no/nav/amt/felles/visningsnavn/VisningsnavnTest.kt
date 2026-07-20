package no.nav.amt.felles.visningsnavn

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg.ValgteFelt
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class VisningsnavnTest {
    @Test
    fun `visningsnavn uses TAO display name`() {
        visningsnavn(Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) shouldBe "Tilrettelagt arbeid i ordinær virksomhet"
    }

    @Test
    fun `hentVisningsnavnFraTiltakskode uses TAO title override`() {
        hentVisningsnavnFraTiltakskode(Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER) shouldBe
            "Tilrettelagt arbeid med oppfølging"
    }

    @Test
    fun `tiltakHosArrangorTekst falls back to unknown arranger`() {
        tiltakHosArrangorTekst("Oppfølging", null) shouldBe "Oppfølging hos Ukjent arrangør"
    }

    @Test
    fun `tiltakHosArrangorIngressTekst uses deltakerliste name for grouped training`() {
        tiltakHosArrangorIngressTekst(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            deltakerlisteNavn = "AMO liste",
            arrangorNavn = "Arrangor 1",
        ) shouldBe "AMO liste hos Arrangor 1"
    }

    @Test
    fun `kladdTiltakHosArrangorTittel keeps tiltak display name for TAO drafts`() {
        kladdTiltakHosArrangorTittel(
            tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
            deltakerlisteNavn = "Tilrettelagt arbeid i ordinær virksomhet",
            arrangorNavn = "Arrangor 1",
            erKladd = true,
        ) shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
    }

    @Test
    fun `hentKurstype uses deterministic ordering for FOV`() {
        val opplaringKategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(
                        UUID.randomUUID() to "Yrkesnorsk",
                        UUID.randomUUID() to "Grunnleggende norsk",
                    ),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )

        hentKurstype(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            opplaringKategoriseringValg = opplaringKategoriseringValg,
        ) shouldBe "Grunnleggende norsk"
    }
}
