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

/**
Disse AI-genererte testene tjener primært til å avdekke regresjoner ved justeringer i logikken.
 */
class VisningsnavnResponseTest {
    private val model = lagGjennomforingModel()
    private val tiltak = model.tiltak
    private val arrangor = model.arrangor

    @Test
    fun `generates title, ingress and draft from Tiltakskode for simple measures`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.OPPFOLGING),
            navn = "Deltakerliste navn",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Oppfølging hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Oppfølging hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Oppfølging hos Arrangor 1"
    }

    @Test
    fun `skalBrukeDeltakerlisteNavn measures use gjennomforing name for ingress and draft`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            navn = "AMO liste",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Arbeidsmarkedsopplæring hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "AMO liste hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "AMO liste hos Arrangor 1"
    }

    @ParameterizedTest(name = "{0} produces same text everywhere")
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
    fun `simple Tiltakskode uses same text for title, ingress and draft`(tiltakskodeStr: String) {
        val tiltakskode = Tiltakskode.valueOf(tiltakskodeStr)
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = tiltakskode),
        )

        val response = VisningsnavnResponse(model)

        // title, ingress and draft should be identical for simple measures
        response.tiltakHosArrangorTittel shouldBe response.tiltakHosArrangorIngressTekst
        response.kladdTiltakHosArrangorTittel shouldBe response.tiltakHosArrangorTittel
    }

    @ParameterizedTest(name = "{0} uses gjennomforing name")
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
    fun `skalBrukeDeltakerlisteNavn Tiltakskode uses gjennomforing name for ingress and draft`(tiltakskodeStr: String) {
        val tiltakskode = Tiltakskode.valueOf(tiltakskodeStr)
        val deltakerlistenavn = "Min spesielle kurs"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = tiltakskode),
            navn = deltakerlistenavn,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorIngressTekst shouldBe "$deltakerlistenavn hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "$deltakerlistenavn hos Arrangor 1"
    }

    @Test
    fun `displays default text when arrangor is null`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.OPPFOLGING),
            arrangor = null,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Oppfølging hos Ukjent arrangør"
        response.tiltakHosArrangorIngressTekst shouldBe "Oppfølging hos Ukjent arrangør"
        response.kladdTiltakHosArrangorTittel shouldBe "Oppfølging hos Ukjent arrangør"
    }

    @Test
    fun `displays arrangor name when provided`() {
        val arrangorNavn = "Arbeidsmarkedsbedrift AS"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.AVKLARING),
            arrangor = arrangor?.copy(navn = arrangorNavn),
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Avklaring hos $arrangorNavn"
        response.tiltakHosArrangorIngressTekst shouldBe "Avklaring hos $arrangorNavn"
        response.kladdTiltakHosArrangorTittel shouldBe "Avklaring hos $arrangorNavn"
    }

    @Test
    fun `NORSKOPPLAERING without selected kurstype shows Tiltakskode display name`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(
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
    fun `TILRETTELAGT_ARBEID_ORDINAER in KLADD uses same text for title and draft title`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
            status = GjennomforingStatusType.KLADD,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Arrangor 1"
    }

    @ParameterizedTest(name = "Status {0}")
    @ValueSource(
        strings = [
            "KLADD",
            "GJENNOMFORES",
            "AVBRUTT",
            "AVLYST",
            "AVSLUTTET",
        ],
    )
    fun `all GjennomforingStatusType values work for simple Tiltakskode`(statusStr: String) {
        val status = GjennomforingStatusType.valueOf(statusStr)
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.OPPFOLGING),
            status = status,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel.isNotEmpty() shouldBe true
        response.tiltakHosArrangorIngressTekst.isNotEmpty() shouldBe true
        response.kladdTiltakHosArrangorTittel.isNotEmpty() shouldBe true
    }

    @ParameterizedTest(name = "Status {0}")
    @ValueSource(
        strings = [
            "KLADD",
            "GJENNOMFORES",
            "AVBRUTT",
            "AVLYST",
            "AVSLUTTET",
        ],
    )
    fun `all GjennomforingStatusType values work for skalBrukeDeltakerlisteNavn Tiltakskode`(statusStr: String) {
        val status = GjennomforingStatusType.valueOf(statusStr)
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            status = status,
            navn = "Kurs namn",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel.isNotEmpty() shouldBe true
        response.tiltakHosArrangorIngressTekst.isNotEmpty() shouldBe true
        response.kladdTiltakHosArrangorTittel.isNotEmpty() shouldBe true
    }

    @Test
    fun `handles long arrangor names`() {
        val langtArrangorNavn = "Veldig Lang Arrangør Navn som kunne være problematisk"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.JOBBKLUBB),
            arrangor = arrangor?.copy(navn = langtArrangorNavn),
            navn = "Jobbklubb kurs",
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Jobbsøkerkurs hos $langtArrangorNavn"
        response.tiltakHosArrangorIngressTekst shouldBe "Jobbsøkerkurs hos $langtArrangorNavn"
        response.kladdTiltakHosArrangorTittel shouldBe "Jobbsøkerkurs hos $langtArrangorNavn"
    }

    @Test
    fun `handles long gjennomforing names`() {
        val langtDeltakerlisteNavn = "Veldig spesialisert arbeidsmarkedsopplæring for målgruppen"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            navn = langtDeltakerlisteNavn,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorIngressTekst shouldBe "$langtDeltakerlisteNavn hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "$langtDeltakerlisteNavn hos Arrangor 1"
    }

    @Test
    fun `uses first selected kurstype when multiple are selected`() {
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
            tiltak = tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "$forsteKurstype hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "$forsteKurstype hos Arrangor 1"
    }

    @Test
    fun `draft title ignores selected kurstype for Norwegian course in KLADD status`() {
        val kursnavn = "Grunnleggende norsk"
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
            tiltak = tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            status = GjennomforingStatusType.KLADD,
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.kladdTiltakHosArrangorTittel shouldBe
            "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
    }

    @Test
    fun `NORSKOPPLAERING with empty kurstype selection shows Tiltakskode display name`() {
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
            tiltak = tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            navn = "FOV kurs",
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangor 1"
    }

    @Test
    fun `draft title shows course arrangement name for multi-participant measures in KLADD status`() {
        val deltakerlisteNavn = "AMO liste med spesiell profil"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            status = GjennomforingStatusType.KLADD,
            navn = deltakerlisteNavn,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Arbeidsmarkedsopplæring hos Arrangor 1"
        response.kladdTiltakHosArrangorTittel shouldBe "$deltakerlisteNavn hos Arrangor 1"
    }

    @Test
    fun `ingress shows course arrangement name for multi-participant measures regardless of status`() {
        val deltakerlisteNavn = "Spesialisert opplæring"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.STUDIESPESIALISERING),
            status = GjennomforingStatusType.KLADD,
            navn = deltakerlisteNavn,
        )

        val response = VisningsnavnResponse(model)

        // Ingress should use course arrangement name even in KLADD status
        response.tiltakHosArrangorIngressTekst shouldBe "$deltakerlisteNavn hos Arrangor 1"
    }

    @Test
    fun `falls back to unknown organizer when arrangor is null for multi-participant measures`() {
        val deltakerlisteNavn = "Arbeidsmarkedsopplæring"
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            arrangor = null,
            navn = deltakerlisteNavn,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Arbeidsmarkedsopplæring hos Ukjent arrangør"
        response.tiltakHosArrangorIngressTekst shouldBe "$deltakerlisteNavn hos Ukjent arrangør"
        response.kladdTiltakHosArrangorTittel shouldBe "$deltakerlisteNavn hos Ukjent arrangør"
    }

    @Test
    fun `falls back to unknown organizer when arrangor is null for Norwegian course with selected kurstype`() {
        val kursnavn = "Norskferdigheter"
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
            tiltak = tiltak.copy(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            ),
            arrangor = null,
        ).copy(opplaringKategoriseringValg = kategoriseringValg)

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "$kursnavn hos Ukjent arrangør"
        response.tiltakHosArrangorIngressTekst shouldBe "$kursnavn hos Ukjent arrangør"
        response.kladdTiltakHosArrangorTittel shouldBe "$kursnavn hos Ukjent arrangør"
    }

    @Test
    fun `falls back to unknown organizer when arrangor is null for TILRETTELAGT_ARBEID_ORDINAER`() {
        val model = lagGjennomforingModel(
            tiltak = tiltak.copy(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
            arrangor = null,
        )

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Ukjent arrangør"
        response.tiltakHosArrangorIngressTekst shouldBe "Tilrettelagt arbeid i ordinær virksomhet hos Ukjent arrangør"
        response.kladdTiltakHosArrangorTittel shouldBe "Tilrettelagt arbeid med oppfølging hos Ukjent arrangør"
    }
}
