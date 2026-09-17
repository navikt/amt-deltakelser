package no.nav.amt.aktivitetskort.domain

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test

class AktivitetskortTest {
    private fun deltakerResponse(
        deltakerliste: Deltakerliste,
        arrangor: Arrangor,
        deltaker: DeltakerDbo = TestData.lagDeltaker(
            deltakerlisteId = deltakerliste.id,
        ),
    ) = TestData.lagDeltakerResponse(deltaker = deltaker, deltakerliste = deltakerliste, arrangor = arrangor)

    private fun lagDeltakerMedGjennomforing(
        deltakerliste: Deltakerliste,
        arrangor: Arrangor,
        deltaker: DeltakerDbo = TestData.lagDeltaker(
            deltakerlisteId = deltakerliste.id,
        ),
    ) = Deltaker.fromDeltakerResponse(
        TestData.lagDeltakerResponse(deltaker = deltaker, deltakerliste = deltakerliste, arrangor = arrangor),
    )

    @Test
    fun `lagDeltakerResponse - deltakerliste og arrangor - lager riktig tittel basert på type tiltak`() {
        val arrangor = TestData.lagArrangor()
        val deltakerlister =
            Tiltakskode.entries
                .map {
                    val tiltaksnavn = when (it) {
                        Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> "Arbforb Tiltak"

                        Tiltakskode.ARBEIDSRETTET_REHABILITERING -> "ARR"

                        Tiltakskode.AVKLARING -> "Avklaringstiltaket"

                        Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> "Digitalt jobbsøkerkurs"

                        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING -> "Grupper AMO"

                        Tiltakskode.JOBBKLUBB -> "Jobbklubben"

                        Tiltakskode.OPPFOLGING -> "Oppfølgingstiltak"

                        Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "VTA"

                        Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER -> "VTAO"
                        Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING -> "Gruppe yrkesfaglig utdanning"

                        Tiltakskode.HOYERE_UTDANNING,
                        Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
                        Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
                        -> "Enkeltplass"

                        else -> "Default tiltaksnavn"
                    }
                    TestData.lagDeltakerliste(tiltak = Tiltak(tiltaksnavn, it), arrangorId = arrangor.id)
                }

        deltakerlister.forEach {
            val aktivitetskortTittel = deltakerResponse(it, arrangor).gjennomforing.visningsnavn.aktivitetskortTittel
            when (it.tiltak.tiltakskode) {
                Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> aktivitetskortTittel shouldBe "Digitalt jobbsøkerkurs hos ${arrangor.navn}"

                Tiltakskode.JOBBKLUBB -> aktivitetskortTittel shouldBe "Jobbsøkerkurs hos ${arrangor.navn}"

                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                -> aktivitetskortTittel shouldBe "Tilrettelagt arbeid hos ${arrangor.navn}"
                Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
                -> aktivitetskortTittel shouldBe "Tilrettelagt arbeid med oppfølging hos ${arrangor.navn}"
                Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                Tiltakskode.STUDIESPESIALISERING,
                Tiltakskode.FAG_OG_YRKESOPPLAERING,
                -> aktivitetskortTittel shouldBe "${it.navn} hos ${arrangor.navn}"

                else -> aktivitetskortTittel shouldBe "${it.tiltak.navn} hos ${arrangor.navn}"
            }
        }
    }

    @Test
    fun `lagDetaljer - deltakerliste med deltakelsesmengde - lager detaljer i riktig rekkefølge`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("VTA 100%", Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET),
        )
        val deltakerDbo = TestData.lagDeltaker(prosentStilling = 100.0, dagerPerUke = 2.5f, deltakerlisteId = deltakerliste.id)
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)
        val deltaker = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo)

        val detaljer = Aktivitetskort.lagDetaljer(deltaker)

        detaljer[0] shouldBe Detalj("Status for deltakelse", displayText(deltaker.status))
        detaljer[1] shouldBe Detalj("Deltakelsesmengde", "100%")
        detaljer[2] shouldBe Detalj("Arrangør", arrangor.navn)
    }

    @Test
    fun `lagDetaljer - deltaker ikke 100 prosent deltakelsesmengde - lager detalj med antall dager i uken`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("AFT 50% 2 dager i uken", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val deltakerDbo = TestData.lagDeltaker(prosentStilling = 50.0, dagerPerUke = 2.0f, deltakerlisteId = deltakerliste.id)
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)
        val deltaker = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo)

        val detaljer = Aktivitetskort.lagDetaljer(deltaker)

        detaljer.first { it.label == "Deltakelsesmengde" } shouldBe Detalj("Deltakelsesmengde", "50% fordelt på 2 dager i uka")
    }

    @Test
    fun `lagDetaljer - deltaker ikke 100 prosent deltakelsesmengde og uten dager per uke - lager detalj med prosent`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("AFT 50%", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)

        val deltakerDbo1 = TestData.lagDeltaker(prosentStilling = 50.0, dagerPerUke = 0.0f, deltakerlisteId = deltakerliste.id)
        val deltakerDbo2 = TestData.lagDeltaker(prosentStilling = 50.0, dagerPerUke = null, deltakerlisteId = deltakerliste.id)
        val response1 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo1)
        val response2 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo2)

        val detaljer1 = Aktivitetskort.lagDetaljer(response1)
        val detaljer2 = Aktivitetskort.lagDetaljer(response2)

        detaljer1.first { it.label == "Deltakelsesmengde" } shouldBe Detalj("Deltakelsesmengde", "50%")
        detaljer2.first { it.label == "Deltakelsesmengde" } shouldBe Detalj("Deltakelsesmengde", "50%")
    }

    @Test
    fun `lagDetaljer - deltaker uten prosentstilling - lager detalj med dager per uke`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("AFT noen dager", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)

        val deltakerDbo1 = TestData.lagDeltaker(prosentStilling = 0.0, dagerPerUke = 5f, deltakerlisteId = deltakerliste.id)
        val deltakerDbo2 = TestData.lagDeltaker(prosentStilling = null, dagerPerUke = 1f, deltakerlisteId = deltakerliste.id)
        val response1 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo1)
        val response2 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo2)

        val detaljer1 = Aktivitetskort.lagDetaljer(response1)
        val detaljer2 = Aktivitetskort.lagDetaljer(response2)

        detaljer1.first { it.label == "Deltakelsesmengde" } shouldBe Detalj("Deltakelsesmengde", "fordelt på 5 dager i uka")
        detaljer2.first { it.label == "Deltakelsesmengde" } shouldBe Detalj("Deltakelsesmengde", "fordelt på 1 dag i uka")
    }

    @Test
    fun `lagDetaljer - deltaker med 0 prosent og 0 dager - lager ikke detalj med deltakelsesmengde`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("AFT", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val deltakerDbo1 = TestData.lagDeltaker(dagerPerUke = 0f, prosentStilling = 0.0)
        val deltakerDbo2 = TestData.lagDeltaker(dagerPerUke = null, prosentStilling = null)
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)
        val response1 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo1)
        val response2 = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo2)

        val detaljer1 = Aktivitetskort.lagDetaljer(response1)
        val detaljer2 = Aktivitetskort.lagDetaljer(response2)

        detaljer1.find { it.label == "Deltakelsesmengde" } shouldBe null
        detaljer2.find { it.label == "Deltakelsesmengde" } shouldBe null
    }

    @Test
    fun `lagDetaljer - TILRETTELAGT_ARBEID_ORDINAER - inkluderer ikke deltakelsesmengde i detaljer`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("Tilrettelagt arbeid i ordinær virksomhet", Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
        )
        val deltakerDbo = TestData.lagDeltaker(deltakerlisteId = deltakerliste.id)
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)
        val deltaker = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo)

        val detaljer = Aktivitetskort.lagDetaljer(deltaker)

        detaljer.find { it.label == "Deltakelsesmengde" } shouldBe null
    }

    @Test
    fun `lagDetaljer - tiltak uten deltakelsesmengde - lager ikke detalj med deltakelsesmengde`() {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltak = Tiltak("Oppfølgingstiltak", Tiltakskode.OPPFOLGING),
        )
        val deltakerDbo = TestData.lagDeltaker()
        val arrangor = TestData.lagArrangor(id = deltakerliste.arrangorId)
        val deltaker = lagDeltakerMedGjennomforing(deltakerliste, arrangor, deltakerDbo)

        val detaljer = Aktivitetskort.lagDetaljer(deltaker)

        detaljer.find { it.label == "Deltakelsesmengde" } shouldBe null
    }
}
