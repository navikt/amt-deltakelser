package no.nav.amt.felles.visningsnavn

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream

class TiltakVisningsnavnTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("specCases")
    fun `visningsnavn følger spec`(case: SpecCase) {
        val response = TiltakVisningsnavn.lagVisningsnavn(
            tiltakskode = case.tiltakskode,
            tiltaksnavn = case.tiltaksnavn,
            gjennomforingsnavn = case.gjennomforingsnavn,
            gjennomforingType = case.type,
            erKladd = case.status == GjennomforingStatusType.KLADD,
            arrangorNavn = case.arrangorNavn,
            opplaringKategoriseringValg = case.kurstype?.let(::lagKurstypeValg),
        )

        case.forventetTittel?.let { response.tittel shouldBe it }
        case.forventetAktivitetskortTittel?.let { response.aktivitetskortTittel shouldBe it }
        case.forventetIngress?.let { response.ingressTekst shouldBe it }
        case.forventetKladd?.let { response.kladdTittel shouldBe it }
    }

    @Test
    fun `kurstype velges deterministisk for norskopplæring`() {
        val response = TiltakVisningsnavn.lagVisningsnavn(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            tiltaksnavn = "Norskopplæring, grunnleggende ferdigheter og FOV",
            gjennomforingsnavn = "Deltakerliste navn",
            gjennomforingType = GjennomforingType.Enkeltplass,
            erKladd = false,
            arrangorNavn = "Arrangor 1",
            opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                        valg = mapOf(
                            UUID.randomUUID() to "Yrkesnorsk",
                            UUID.randomUUID() to "Almenn norsk",
                        ),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            ),
        )

        response.tittel shouldBe "Almenn norsk hos Arrangor 1"
        response.aktivitetskortTittel shouldBe "Almenn norsk hos Arrangor 1"
        response.ingressTekst shouldBe "Almenn norsk hos Arrangor 1"
    }

    @Test
    fun `'Ukjent arrangør' brukes som fallback`() {
        val response = TiltakVisningsnavn.lagVisningsnavn(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            tiltaksnavn = "Arbeidsforberedende trening",
            gjennomforingsnavn = "Deltakerliste navn",
            gjennomforingType = GjennomforingType.Gruppe,
            erKladd = false,
            arrangorNavn = null,
        )

        response.tittel shouldBe "Arbeidsforberedende trening hos Ukjent arrangør"
        response.aktivitetskortTittel shouldBe "Arbeidsforberedende trening hos Ukjent arrangør"
        response.ingressTekst shouldBe "Arbeidsforberedende trening hos Ukjent arrangør"
        response.kladdTittel shouldBe "Arbeidsforberedende trening hos Ukjent arrangør"
    }

    data class SpecCase(
        val navn: String,
        val tiltakskode: Tiltakskode,
        val type: GjennomforingType = GjennomforingType.Gruppe,
        val status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        val tiltaksnavn: String,
        val gjennomforingsnavn: String,
        val arrangorNavn: String?,
        val kurstype: String? = null,
        val forventetTittel: String?,
        val forventetAktivitetskortTittel: String?,
        val forventetIngress: String?,
        val forventetKladd: String?,
    ) {
        override fun toString(): String = navn
    }

    companion object {
        /**
         * Tester basert på spec i [Confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=800078787&spaceKey=SFAMT&title=Visning%2Bav%2Bnavn%2Bp%C3%A5%2Btiltaket%2Bgjennomf%C3%B8ring%2Bi%2Baktivitetsplanenen%2Bog%2Bp%C3%A5%2Btiltakssidene)
         */
        @JvmStatic
        fun specCases(): Stream<SpecCase> = Stream.of(
            SpecCase(
                navn = "arbeidsforberedende trening bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                tiltaksnavn = "Arbeidsforberedende trening",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsforberedende trening hos Arrangør navn",
                forventetAktivitetskortTittel = "Arbeidsforberedende trening hos Arrangør navn",
                forventetIngress = "Arbeidsforberedende trening hos Arrangør navn",
                forventetKladd = "Arbeidsforberedende trening hos Arrangør navn",
            ),
            SpecCase(
                navn = "varig tilrettelagt arbeid i skjermet virksomhet bruker egendefinert tittel og tiltakstype i ingress",
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                tiltaksnavn = "Varig tilrettelagt arbeid i skjermet virksomhet",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Tilrettelagt arbeid hos Arrangør navn",
                forventetAktivitetskortTittel = "Tilrettelagt arbeid hos Arrangør navn",
                forventetIngress = "Varig tilrettelagt arbeid i skjermet virksomhet hos Arrangør navn",
                forventetKladd = null,
            ),
            SpecCase(
                navn = "varig tilrettelagt arbeid i skjermet virksomhet bruker tiltakstype i kladd",
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Varig tilrettelagt arbeid i skjermet virksomhet",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Tilrettelagt arbeid hos Arrangør navn",
                forventetAktivitetskortTittel = "Tilrettelagt arbeid hos Arrangør navn",
                forventetIngress = "Varig tilrettelagt arbeid i skjermet virksomhet hos Arrangør navn",
                forventetKladd = "Varig tilrettelagt arbeid i skjermet virksomhet hos Arrangør navn",
            ),
            SpecCase(
                navn = "oppfolging bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.OPPFOLGING,
                tiltaksnavn = "Oppfølging",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Oppfølging hos Arrangør navn",
                forventetAktivitetskortTittel = "Oppfølging hos Arrangør navn",
                forventetIngress = "Oppfølging hos Arrangør navn",
                forventetKladd = "Oppfølging hos Arrangør navn",
            ),
            SpecCase(
                navn = "avklaring bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.AVKLARING,
                tiltaksnavn = "Avklaring",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Avklaring hos Arrangør navn",
                forventetAktivitetskortTittel = "Avklaring hos Arrangør navn",
                forventetIngress = "Avklaring hos Arrangør navn",
                forventetKladd = "Avklaring hos Arrangør navn",
            ),
            SpecCase(
                navn = "arbeidsrettet rehabilitering bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING,
                tiltaksnavn = "Arbeidsrettet rehabilitering",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsrettet rehabilitering hos Arrangør navn",
                forventetAktivitetskortTittel = "Arbeidsrettet rehabilitering hos Arrangør navn",
                forventetIngress = "Arbeidsrettet rehabilitering hos Arrangør navn",
                forventetKladd = "Arbeidsrettet rehabilitering hos Arrangør navn",
            ),
            SpecCase(
                navn = "digitalt oppfolgingstiltak bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
                tiltaksnavn = "Digitalt jobbsøkerkurs",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Digitalt jobbsøkerkurs hos Arrangør navn",
                forventetAktivitetskortTittel = "Digitalt jobbsøkerkurs hos Arrangør navn",
                forventetIngress = "Digitalt jobbsøkerkurs hos Arrangør navn",
                forventetKladd = "Digitalt jobbsøkerkurs hos Arrangør navn",
            ),
            SpecCase(
                navn = "jobbklubb bruker egendefinert navn i alle tre felter",
                tiltakskode = Tiltakskode.JOBBKLUBB,
                tiltaksnavn = "Jobbklubb",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Jobbsøkerkurs hos Arrangør navn",
                forventetAktivitetskortTittel = "Jobbsøkerkurs hos Arrangør navn",
                forventetIngress = "Jobbsøkerkurs hos Arrangør navn",
                forventetKladd = "Jobbsøkerkurs hos Arrangør navn",
            ),
            SpecCase(
                navn = "gruppe arbeidsmarkedsopplaering bruker tiltakstypen i tittel og deltakerlistenavn ellers",
                tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                tiltaksnavn = "Arbeidsmarkedsopplæring (gruppe)",
                gjennomforingsnavn = "AMO gruppe 1",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsmarkedsopplæring (gruppe) hos Arrangør navn",
                forventetAktivitetskortTittel = "AMO gruppe 1 hos Arrangør navn",
                forventetIngress = "AMO gruppe 1 hos Arrangør navn",
                forventetKladd = "AMO gruppe 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "gruppe fag og yrkesopplaering bruker tiltakstypen i tittel og deltakerlistenavn ellers",
                tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                tiltaksnavn = "Fag- og yrkesopplæring (gruppe)",
                gjennomforingsnavn = "Faggruppe 1",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Fag- og yrkesopplæring (gruppe) hos Arrangør navn",
                forventetAktivitetskortTittel = "Faggruppe 1 hos Arrangør navn",
                forventetIngress = "Faggruppe 1 hos Arrangør navn",
                forventetKladd = "Faggruppe 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "arbeidsmarkedsopplaering enkeltplass bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
                tiltaksnavn = "Arbeidsmarkedsopplæring (AMO)",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsmarkedsopplæring (AMO) hos Arrangør navn",
                forventetAktivitetskortTittel = "Arbeidsmarkedsopplæring (AMO) hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "fag og yrkesopplaering enkeltplass bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
                tiltaksnavn = "Fag- og yrkesopplæring",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Fag- og yrkesopplæring hos Arrangør navn",
                forventetAktivitetskortTittel = "Fag- og yrkesopplæring hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "arbeidsmarkedsopplaering gruppevariant bruker tiltakstypen i tittel og deltakerlistenavn ellers",
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Arbeidsmarkedsopplæring (AMO)",
                gjennomforingsnavn = "AMO liste",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsmarkedsopplæring (AMO) hos Arrangør navn",
                forventetAktivitetskortTittel = "AMO liste hos Arrangør navn",
                forventetIngress = "AMO liste hos Arrangør navn",
                forventetKladd = "AMO liste hos Arrangør navn",
            ),
            SpecCase(
                navn = "arbeidsmarkedsopplaering enkeltplassvariant bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                type = GjennomforingType.Enkeltplass,
                tiltaksnavn = "Arbeidsmarkedsopplæring (AMO)",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Arbeidsmarkedsopplæring (AMO) hos Arrangør navn",
                forventetAktivitetskortTittel = "Arbeidsmarkedsopplæring (AMO) hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "norskopplaering i gruppevariant bruker deltakerlistenavn når kurstype ikke er valgt",
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Norskopplæring, grunnleggende ferdigheter og FOV",
                gjennomforingsnavn = "Norskkurs kull 1",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangør navn",
                forventetAktivitetskortTittel = "Norskkurs kull 1 hos Arrangør navn",
                forventetIngress = "Norskkurs kull 1 hos Arrangør navn",
                forventetKladd = "Norskkurs kull 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "norskopplaering i enkeltplass bruker kurstype i tittel og ingress, men tiltakstype i kladd",
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                type = GjennomforingType.Enkeltplass,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Norskopplæring, grunnleggende ferdigheter og FOV",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                kurstype = "Yrkesnorsk",
                forventetTittel = "Yrkesnorsk hos Arrangør navn",
                forventetAktivitetskortTittel = "Yrkesnorsk hos Arrangør navn",
                forventetIngress = "Yrkesnorsk hos Arrangør navn",
                forventetKladd = "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangør navn",
            ),
            SpecCase(
                navn = "studiespesialisering gruppevariant bruker tiltakstypen i tittel og deltakerlistenavn ellers",
                tiltakskode = Tiltakskode.STUDIESPESIALISERING,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Studiespesialisering",
                gjennomforingsnavn = "Studiespes kull 1",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Studiespesialisering hos Arrangør navn",
                forventetAktivitetskortTittel = "Studiespes kull 1 hos Arrangør navn",
                forventetIngress = "Studiespes kull 1 hos Arrangør navn",
                forventetKladd = "Studiespes kull 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "studiespesialisering enkeltplass bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.STUDIESPESIALISERING,
                type = GjennomforingType.Enkeltplass,
                tiltaksnavn = "Studiespesialisering",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Studiespesialisering hos Arrangør navn",
                forventetAktivitetskortTittel = "Studiespesialisering hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "fag og yrkesopplaering gruppevariant bruker tiltakstypen i tittel og deltakerlistenavn ellers",
                tiltakskode = Tiltakskode.FAG_OG_YRKESOPPLAERING,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Fag- og yrkesopplæring",
                gjennomforingsnavn = "Fagutdanning kull 1",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Fag- og yrkesopplæring hos Arrangør navn",
                forventetAktivitetskortTittel = "Fagutdanning kull 1 hos Arrangør navn",
                forventetIngress = "Fagutdanning kull 1 hos Arrangør navn",
                forventetKladd = "Fagutdanning kull 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "fag og yrkesopplaering enkeltplass bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.FAG_OG_YRKESOPPLAERING,
                type = GjennomforingType.Enkeltplass,
                tiltaksnavn = "Fag- og yrkesopplæring",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Fag- og yrkesopplæring hos Arrangør navn",
                forventetAktivitetskortTittel = "Fag- og yrkesopplæring hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "hoyere yrkesfaglig utdanning bruker full tiltakstype i tittel",
                tiltakskode = Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
                type = GjennomforingType.Enkeltplass,
                tiltaksnavn = "Fagskole (høyere yrkesfaglig utdanning)",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Fagskole (høyere yrkesfaglig utdanning) hos Arrangør navn",
                forventetAktivitetskortTittel = "Fagskole (høyere yrkesfaglig utdanning) hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "hoyere utdanning bruker tiltakstypen i tittel",
                tiltakskode = Tiltakskode.HOYERE_UTDANNING,
                type = GjennomforingType.Enkeltplass,
                tiltaksnavn = "Høyere utdanning",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Høyere utdanning hos Arrangør navn",
                forventetAktivitetskortTittel = "Høyere utdanning hos Arrangør navn",
                forventetIngress = null,
                forventetKladd = null,
            ),
            SpecCase(
                navn = "tilrettelagt arbeid i ordinær virksomhet bruker egendefinert tittel men tiltakstype i ingress",
                tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Tilrettelagt arbeid i ordinær virksomhet",
                gjennomforingsnavn = "Deltakerliste navn",
                arrangorNavn = "Arrangør navn",
                forventetTittel = "Tilrettelagt arbeid med oppfølging hos Arrangør navn",
                forventetAktivitetskortTittel = "Tilrettelagt arbeid med oppfølging hos Arrangør navn",
                forventetIngress = "Tilrettelagt arbeid i ordinær virksomhet hos Arrangør navn",
                forventetKladd = "Tilrettelagt arbeid med oppfølging hos Arrangør navn",
            ),
        )

        private fun lagKurstypeValg(kurstype: String) = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(UUID.randomUUID() to kurstype),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
    }
}
