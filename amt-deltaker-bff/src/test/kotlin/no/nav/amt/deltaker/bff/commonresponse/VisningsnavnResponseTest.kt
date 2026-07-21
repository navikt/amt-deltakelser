package no.nav.amt.deltaker.bff.commonresponse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingModel
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
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

class VisningsnavnResponseTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("specCases")
    fun `visningsnavn følger spec for feltene denne responsen eier`(case: SpecCase) {
        val model = lagGjennomforingModel(
            type = case.type,
            tiltak = lagTiltakstype(
                tiltakskode = case.tiltakskode,
                navn = case.tiltaksnavn,
            ),
            navn = case.gjennomforingsnavn,
            status = case.status,
            arrangor = case.arrangorNavn?.let { arrangorNavn ->
                no.nav.amt.deltaker.bff.model.ArrangorModel(
                    navn = arrangorNavn,
                    organisasjonsnummer = "123456789",
                )
            },
        ).copy(opplaringKategoriseringValg = case.kurstype?.let(::lagKurstypeValg))

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe case.forventetTittel
        response.tiltakHosArrangorIngressTekst shouldBe case.forventetIngress
        response.kladdTiltakHosArrangorTittel shouldBe case.forventetKladd
    }

    @Test
    fun `kurstype velges deterministisk for norskopplaering`() {
        val model = lagGjennomforingModel(
            type = GjennomforingType.Enkeltplass,
            tiltak = lagTiltakstype(
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                navn = "Norskopplæring, grunnleggende ferdigheter og FOV",
            ),
        ).copy(
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

        val response = VisningsnavnResponse(model)

        response.tiltakHosArrangorTittel shouldBe "Almenn norsk hos Arrangor 1"
        response.tiltakHosArrangorIngressTekst shouldBe "Almenn norsk hos Arrangor 1"
    }

    data class SpecCase(
        val navn: String,
        val tiltakskode: Tiltakskode,
        val type: GjennomforingType = GjennomforingType.Gruppe,
        val status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        val tiltaksnavn: String = "Tiltakstype navn",
        val gjennomforingsnavn: String = "Deltakerliste navn",
        val arrangorNavn: String? = "Arrangør navn",
        val kurstype: String? = null,
        val forventetTittel: String,
        val forventetIngress: String,
        val forventetKladd: String,
    ) {
        override fun toString(): String = navn
    }

    companion object {
        @JvmStatic
        fun specCases(): Stream<SpecCase> = Stream.of(
            SpecCase(
                navn = "enkle tiltak bruker tiltakstypen i alle tre felter",
                tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                tiltaksnavn = "Arbeidsforberedende trening",
                forventetTittel = "Arbeidsforberedende trening hos Arrangør navn",
                forventetIngress = "Arbeidsforberedende trening hos Arrangør navn",
                forventetKladd = "Arbeidsforberedende trening hos Arrangør navn",
            ),
            SpecCase(
                navn = "jobbklubb bruker egendefinert navn i alle tre felter",
                tiltakskode = Tiltakskode.JOBBKLUBB,
                tiltaksnavn = "Jobbklubb",
                forventetTittel = "Jobbsøkerkurs hos Arrangør navn",
                forventetIngress = "Jobbsøkerkurs hos Arrangør navn",
                forventetKladd = "Jobbsøkerkurs hos Arrangør navn",
            ),
            SpecCase(
                navn = "varig tilrettelagt arbeid i skjermet virksomhet skiller mellom tittel og kladd",
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Varig tilrettelagt arbeid i skjermet virksomhet",
                forventetTittel = "Tilrettelagt arbeid hos Arrangør navn",
                forventetIngress = "Varig tilrettelagt arbeid i skjermet virksomhet hos Arrangør navn",
                forventetKladd = "Varig tilrettelagt arbeid i skjermet virksomhet hos Arrangør navn",
            ),
            SpecCase(
                navn = "tilrettelagt arbeid i ordinær virksomhet bruker egendefinert tittel men tiltakstype i ingress",
                tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Tilrettelagt arbeid i ordinær virksomhet",
                forventetTittel = "Tilrettelagt arbeid med oppfølging hos Arrangør navn",
                forventetIngress = "Tilrettelagt arbeid i ordinær virksomhet hos Arrangør navn",
                forventetKladd = "Tilrettelagt arbeid med oppfølging hos Arrangør navn",
            ),
            SpecCase(
                navn = "gruppebasert arbeidsmarkedsopplæring bruker deltakerlistenavn i ingress og kladd",
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Arbeidsmarkedsopplæring",
                gjennomforingsnavn = "AMO liste",
                forventetTittel = "Arbeidsmarkedsopplæring hos Arrangør navn",
                forventetIngress = "AMO liste hos Arrangør navn",
                forventetKladd = "AMO liste hos Arrangør navn",
            ),
            SpecCase(
                navn = "norskopplaering i gruppevariant bruker deltakerlistenavn når kurstype ikke er valgt",
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                type = GjennomforingType.Gruppe,
                tiltaksnavn = "Norskopplæring, grunnleggende ferdigheter og FOV",
                gjennomforingsnavn = "Norskkurs kull 1",
                forventetTittel = "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangør navn",
                forventetIngress = "Norskkurs kull 1 hos Arrangør navn",
                forventetKladd = "Norskkurs kull 1 hos Arrangør navn",
            ),
            SpecCase(
                navn = "norskopplaering i enkeltplass bruker kurstype i tittel og ingress, men tiltakstype i kladd",
                tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                type = GjennomforingType.Enkeltplass,
                status = GjennomforingStatusType.KLADD,
                tiltaksnavn = "Norskopplæring, grunnleggende ferdigheter og FOV",
                kurstype = "Yrkesnorsk",
                forventetTittel = "Yrkesnorsk hos Arrangør navn",
                forventetIngress = "Yrkesnorsk hos Arrangør navn",
                forventetKladd = "Norskopplæring, grunnleggende ferdigheter og FOV hos Arrangør navn",
            ),
            SpecCase(
                navn = "ukjent arrangør brukes som fallback",
                tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                tiltaksnavn = "Arbeidsforberedende trening",
                arrangorNavn = null,
                forventetTittel = "Arbeidsforberedende trening hos Ukjent arrangør",
                forventetIngress = "Arbeidsforberedende trening hos Ukjent arrangør",
                forventetKladd = "Arbeidsforberedende trening hos Ukjent arrangør",
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
