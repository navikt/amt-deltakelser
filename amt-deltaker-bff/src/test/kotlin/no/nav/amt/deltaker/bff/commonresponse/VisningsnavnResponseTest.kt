package no.nav.amt.deltaker.bff.commonresponse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingModel
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class VisningsnavnResponseTest {
    @Test
    fun `constructor - default bygger tittel og ingress fra tiltakskode visningsnavn`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.OPPFOLGING),
            navn = "Deltakerliste navn",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Oppfølging hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Oppfølging hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Oppfølging hos Arrangor 1"
    }

    @Test
    fun `constructor - tilrettelagt arbeid ordinær har spesialtekst i tittel`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
            navn = "Deltakerliste navn",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Tilrettelagt arbeid i ordinær virksomhet hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
    }

    @Test
    fun `constructor - ingress bruker deltakerlistenavn for tiltakskoder som krever det`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            navn = "AMO liste",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Arbeidsmarkedsopplæring hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "AMO liste hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "AMO liste hos Arrangor 1"
    }

    @Test
    fun `constructor - kurstype overstyrer tittel og ingress for FOV`() {
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(UUID.randomUUID() to "Grunnleggende ferdigheter"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            navn = "FOV liste",
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Grunnleggende ferdigheter hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Grunnleggende ferdigheter hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Grunnleggende ferdigheter hos Arrangor 1"
    }

    @Test
    fun `constructor - kladd tittel ignorerer kurstype for kladd status`() {
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(UUID.randomUUID() to "Grunnleggende ferdigheter"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            status = GjennomforingStatusType.KLADD,
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Grunnleggende ferdigheter hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
    }
}
