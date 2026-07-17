package no.nav.amt.deltaker.bff.commonresponse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingModel
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @ParameterizedTest(name = "{0} får tiltakskode som tittel")
    @ValueSource(
        strings = [
            "ARBEIDSFORBEREDENDE_TRENING",
            "ARBEIDSRETTET_REHABILITERING",
            "AVKLARING",
            "OPPFOLGING",
            "VARIG_TILRETTELAGT_ARBEID_SKJERMET",
            "DIGITALT_OPPFOLGINGSTILTAK",
            "JOBBKLUBB",
            "ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING",
            "ENKELTPLASS_FAG_OG_YRKESOPPLAERING",
        ],
    )
    fun `Individuelle tiltak viser tiltakskode som tittel og ingress`(tiltakskodeStr: String) {
        val tiltakskode = Tiltakskode.valueOf(tiltakskodeStr)
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = tiltakskode),
        )

        val response = VisningsnavnResponse(model)

        // Tittel og ingress skal være like for individuelle tiltak
        response.tiltakHosArrangorTittel shouldBe response.tiltakHosArrangorIngressTekst
        response.kladdTiltakHosArrangorTittel shouldBe response.tiltakHosArrangorTittel
    }

    @ParameterizedTest(name = "{0} bruker deltakerlistenavn i ingress og kladd")
    @ValueSource(
        strings = [
            "GRUPPE_ARBEIDSMARKEDSOPPLAERING",
            "GRUPPE_FAG_OG_YRKESOPPLAERING",
            "ARBEIDSMARKEDSOPPLAERING",
            "NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV",
            "STUDIESPESIALISERING",
            "FAG_OG_YRKESOPPLAERING",
            "HOYERE_YRKESFAGLIG_UTDANNING",
            "HOYERE_UTDANNING",
        ],
    )
    fun `Kursbasert tiltak bruker deltakerlistenavn i ingress og kladd tittel`(tiltakskodeStr: String) {
        val tiltakskode = Tiltakskode.valueOf(tiltakskodeStr)
        val deltakerlistenavn = "Min spesielle kurs"
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = tiltakskode),
            navn = deltakerlistenavn,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorIngressTekst shouldBe "$deltakerlistenavn hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "$deltakerlistenavn hos Arrangor 1"
    }

    @Test
    fun `Ukjent arrangør vises som standardtekst når arrangør er null`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.OPPFOLGING),
            arrangor = null,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Oppfølging hos Ukjent arrangør"
        response.tiltakHosArrangorIngressTekst shouldBe "Oppfølging hos Ukjent arrangør"
        response.kladdTiltakHosArrangorTittel shouldBe "Oppfølging hos Ukjent arrangør"
    }

    @Test
    fun `Arrangørens navn vises korrekt når den er spesifisert`() {
        val arrangorNavn = "Arbeidsmarkedsbedrift AS"
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.AVKLARING),
            arrangor = lagGjennomforingModel().arrangor?.copy(navn = arrangorNavn),
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Avklaring hos $arrangorNavn"
        response.tiltakHosArrangorIngressTekst shouldBe "Avklaring hos $arrangorNavn"
        response.kladdTiltakHosArrangorTittel shouldBe "Avklaring hos $arrangorNavn"
    }

    @Test
    fun `FOV uten kurstype vises som standard`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            navn = "FOV kurs uten valgt type",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "FOV kurs uten valgt type hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "FOV kurs uten valgt type hos Arrangor 1"
    }

    @Test
    fun `FOV med kurstype viser kursnavn overalt`() {
        val kursnavn = "Norskferdigheter for arbeidsmarkedet"
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(UUID.randomUUID() to kursnavn),
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

        response.tiltakHosArrangorTittel shouldBe "$kursnavn hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "$kursnavn hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "$kursnavn hos Arrangor 1"
    }

    @Test
    fun `FOV med kurstype i kladd status ignorerer kurstype i kladd tittel`() {
        val kursnavn = "Norskferdigheter for arbeidsmarkedet"
        val deltakerlisteNavn = "FOV liste"
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(UUID.randomUUID() to kursnavn),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            status = GjennomforingStatusType.KLADD,
            navn = deltakerlisteNavn,
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "$kursnavn hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
    }

    @Test
    fun `Tilrettelagt arbeid ordinær har egen tekst for ingress men ikke for kladd`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Tilrettelagt arbeid i ordinær virksomhet hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
    }

    @Test
    fun `Tilrettelagt arbeid ordinær i kladd status`() {
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
            status = GjennomforingStatusType.KLADD,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
    }

    @Test
    fun `FOV med flere kurstyper bruker den første`() {
        val forsteKurstype = "Grunnleggende norsk"
        val andreKurstype = "Yrkesnorsk"
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(
                        UUID.randomUUID() to forsteKurstype,
                        UUID.randomUUID() to andreKurstype,
                    ),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel.contains("hos Arrangor 1") shouldBe true
    }

    @Test
    fun `FOV med tomt valg av kurstype vises som standard`() {
        val kategoriseringValg = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = emptyMap(),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
        val model = lagGjennomforingModel(
            tiltak = lagGjennomforingModel().tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            navn = "FOV kurs",
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
    }
}
