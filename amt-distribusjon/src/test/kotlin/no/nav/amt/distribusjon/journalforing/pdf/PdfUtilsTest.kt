package no.nav.amt.distribusjon.journalforing.pdf

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.hendelse.model.visningsnavn
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Persondata
import no.nav.amt.distribusjon.utils.formatDateWithMonthName
import no.nav.amt.internapi.hendelse.InnholdDto
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.journalforing.pdf.EndringDto
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PdfUtilsTest {
    @Nested
    inner class ToInnholdPdfDtoTests {
        @Test
        fun `innhold med innholdskode == annet`() {
            val innholdListeInTest = listOf(
                Innhold(
                    tekst = "tekst 1",
                    innholdskode = "annet",
                    valgt = true,
                    beskrivelse = "~innholdsbeskrivelse~",
                ),
            )

            val innholdPdfDto = innholdListeInTest.toInnholdPdfDto("~ledetekst~")

            assertSoftly(innholdPdfDto.shouldNotBeNull()) {
                valgteInnholdselementer.shouldBeEmpty()
                fritekstBeskrivelse shouldBe "~innholdsbeskrivelse~"
                ledetekst shouldBe "~ledetekst~"
            }
        }

        @Test
        fun `innhold med innholdskode != annet`() {
            val innholdListeInTest = listOf(
                Innhold(
                    tekst = "tekst 1",
                    innholdskode = "~innholdskode~",
                    valgt = true,
                    beskrivelse = "~innholdsbeskrivelse~",
                ),
            )

            val innholdPdfDto = innholdListeInTest.toInnholdPdfDto("~ledetekst~")

            assertSoftly(innholdPdfDto.shouldNotBeNull()) {
                valgteInnholdselementer shouldBe listOf("tekst 1: ~innholdsbeskrivelse~")
                fritekstBeskrivelse shouldBe null
                ledetekst shouldBe "~ledetekst~"
            }
        }

        @Test
        fun `tomt innhold uten ledetekst`() {
            val innholdListeInTest = emptyList<Innhold>()

            val innholdPdfDto = innholdListeInTest.toInnholdPdfDto(null)

            innholdPdfDto.shouldBeNull()
        }

        @Test
        fun `tomt innhold med ledetekst`() {
            val innholdListeInTest = emptyList<Innhold>()

            val innholdPdfDto = innholdListeInTest.toInnholdPdfDto("~ledetekst~")

            assertSoftly(innholdPdfDto.shouldNotBeNull()) {
                valgteInnholdselementer.shouldBeEmpty()
                fritekstBeskrivelse shouldBe null
                ledetekst shouldBe "~ledetekst~"
            }
        }
    }

    @Nested
    inner class LagEndringsvedtakPdfDtoTests {
        @Test
        fun `lagEndringsvedtakPdfDto - to endringer av samme type - bruker nyeste endring`() {
            val deltaker = Hendelsesdata.lagDeltaker()
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.forlengDeltakelse(sluttdato = LocalDate.now().plusWeeks(3)),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
                Hendelsesdata.hendelse(
                    HendelseTypeData.forlengDeltakelse(sluttdato = LocalDate.now().plusWeeks(4)),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now(),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            (pdfDto.endringer.first() as EndringDto.ForlengDeltakelse).tittel shouldBe "Deltakelsen er forlenget til ${
                LocalDate.now().plusWeeks(4).formatDateWithMonthName()
            }"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - IkkeAktuell - inneholder arsak som string`() {
            val deltaker = Hendelsesdata.lagDeltaker()
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val arsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT)
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.ikkeAktuell(arsak),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            (pdfDto.endringer.first() as EndringDto.IkkeAktuell).aarsak shouldBe arsak.visningsnavn()
        }

        @Test
        fun `lagEndringsvedtakPdfDto - EndreInnhold - inneholder innhold som string`() {
            val deltaker = Hendelsesdata.lagDeltaker()
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val innhold = listOf(
                InnholdDto("tekst 1", "kode 1", null),
                InnholdDto("tekst 2", "kode 2", null),
                InnholdDto("annet tekst", "annet", "beskrivelse"),
            )
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.endreInnhold(innhold),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innhold shouldBe listOf("tekst 1", "tekst 2", "beskrivelse")
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innholdBeskrivelse shouldBe null
        }

        @Test
        fun `lagEndringsvedtakPdfDto - EndreInnhold, VTA - inneholder innholdsbeskrivelse`() {
            val deltaker =
                Hendelsesdata.lagDeltaker(
                    deltakerliste = Hendelsesdata.lagDeltakerliste(
                        tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET),
                    ),
                )
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val innhold = listOf(
                InnholdDto("annet tekst", "annet", "beskrivelse"),
            )
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.endreInnhold(innhold),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innhold shouldBe listOf("beskrivelse")
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innholdBeskrivelse shouldBe "beskrivelse"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - EndreInnhold, TILRETTELAGT_ARBEID_ORDINAER - inneholder innholdsbeskrivelse`() {
            val deltaker =
                Hendelsesdata.lagDeltaker(
                    deltakerliste = Hendelsesdata.lagDeltakerliste(
                        tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
                    ),
                )
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val innhold = listOf(
                InnholdDto("annet tekst", "annet", "beskrivelse"),
            )
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.endreInnhold(innhold),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innhold shouldBe listOf("beskrivelse")
            (pdfDto.endringer.first() as EndringDto.EndreInnhold).innholdBeskrivelse shouldBe "beskrivelse"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - EndreDeltakelsesmengde for enkeltplass viser kun dager i uka`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste().copy(erEnkeltplass = true)
            val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.endreDeltakelsesmengde(
                        deltakelsesprosent = 50F,
                        dagerPerUke = 3F,
                    ),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            val endring = pdfDto.endringer.first() as EndringDto.EndreDeltakelsesmengde
            endring.tittel shouldBe "Deltakelsen er endret til 3 dager i uka"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - EndreDeltakelsesmengde for gruppe viser prosent fordelt pa dager`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste().copy(erEnkeltplass = false)
            val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.endreDeltakelsesmengde(
                        deltakelsesprosent = 50F,
                        dagerPerUke = 3F,
                    ),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            val endring = pdfDto.endringer.first() as EndringDto.EndreDeltakelsesmengde
            endring.tittel shouldBe "Deltakelsen er endret til 50 % fordelt på 3 dager i uka"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - Avslutt deltakelse, opplæringstiltak, har fullført - tar med fullført og deltatt `() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                oppstartstype = Oppstartstype.LOPENDE,
                tiltak = Hendelsesdata.tiltak(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.avsluttDeltakelse(harFullfort = true),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            val avsluttDeltakelseResult = (pdfDto.endringer.first() as EndringDto.AvsluttDeltakelse)
            avsluttDeltakelseResult.harFullfort shouldBe "Ja"
            avsluttDeltakelseResult.harDeltatt shouldBe "Ja"
        }

        @Test
        fun `lagEndringsvedtakPdfDto - Avslutt deltakelse, opplæringstiltak, har ikke fullført - tar med fullført og deltatt `() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                oppstartstype = Oppstartstype.LOPENDE,
                tiltak = Hendelsesdata.tiltak(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
            val navBruker = Persondata.lagNavBruker()
            val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
            val hendelser: List<Hendelse> = listOf(
                Hendelsesdata.hendelse(
                    HendelseTypeData.avsluttDeltakelse(harFullfort = false),
                    deltaker = deltaker,
                    ansvarlig = ansvarligNavVeileder,
                    opprettet = LocalDateTime.now().minusMinutes(20),
                ),
            )

            val pdfDto = lagEndringsvedtakPdfDto(
                deltaker = deltaker,
                navBruker = navBruker,
                ansvarlig = ansvarligNavVeileder,
                hendelser = hendelser,
                opprettetDato = LocalDate.now(),
            )

            pdfDto.endringer.size shouldBe 1
            val avsluttDeltakelseResult = (pdfDto.endringer.first() as EndringDto.AvsluttDeltakelse)
            avsluttDeltakelseResult.harFullfort shouldBe "Nei"
            avsluttDeltakelseResult.harDeltatt shouldBe "Ja"
        }
    }

    @Test
    fun `lagEndringsvedtakPdfDto - Avslutt deltakelse, individuelle tiltak - tar ikke med om deltaker har fullført og deltatt `() {
        val deltakerliste = Hendelsesdata.lagDeltakerliste(
            oppstartstype = Oppstartstype.LOPENDE,
            tiltak = Hendelsesdata.tiltak(
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            ),
        )
        val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
        val navBruker = Persondata.lagNavBruker()
        val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
        val hendelser: List<Hendelse> = listOf(
            Hendelsesdata.hendelse(
                HendelseTypeData.avsluttDeltakelse(harFullfort = true),
                deltaker = deltaker,
                ansvarlig = ansvarligNavVeileder,
                opprettet = LocalDateTime.now().minusMinutes(20),
            ),
        )

        val pdfDto = lagEndringsvedtakPdfDto(
            deltaker = deltaker,
            navBruker = navBruker,
            ansvarlig = ansvarligNavVeileder,
            hendelser = hendelser,
            opprettetDato = LocalDate.now(),
        )

        pdfDto.endringer.size shouldBe 1
        val avsluttDeltakelseResult = (pdfDto.endringer.first() as EndringDto.AvsluttDeltakelse)
        avsluttDeltakelseResult.harFullfort shouldBe null
        avsluttDeltakelseResult.harDeltatt shouldBe null
    }

    @Test
    fun `lagEndringsvedtakPdfDto - Endre avslutning, harFullført - tar med om deltaker har fullført og deltatt `() {
        val deltakerliste = Hendelsesdata.lagDeltakerliste(
            oppstartstype = Oppstartstype.LOPENDE,
            tiltak = Hendelsesdata.tiltak(
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            ),
        )
        val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
        val navBruker = Persondata.lagNavBruker()
        val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
        val hendelser: List<Hendelse> = listOf(
            Hendelsesdata.hendelse(
                HendelseTypeData.endreAvsluttDeltakelse(harFullfort = true),
                deltaker = deltaker,
                ansvarlig = ansvarligNavVeileder,
                opprettet = LocalDateTime.now().minusMinutes(20),
            ),
        )

        val pdfDto = lagEndringsvedtakPdfDto(
            deltaker = deltaker,
            navBruker = navBruker,
            ansvarlig = ansvarligNavVeileder,
            hendelser = hendelser,
            opprettetDato = LocalDate.now(),
        )

        pdfDto.endringer.size shouldBe 1
        val avsluttDeltakelseResult = (pdfDto.endringer.first() as EndringDto.EndreAvslutning)
        avsluttDeltakelseResult.harFullfort shouldBe "Ja"
    }

    @Test
    fun `lagEndringsvedtakPdfDto - Endre avslutning, harFullført=false - tar med om deltaker har fullført og deltatt `() {
        val deltakerliste = Hendelsesdata.lagDeltakerliste(
            oppstartstype = Oppstartstype.LOPENDE,
            tiltak = Hendelsesdata.tiltak(
                tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            ),
        )
        val deltaker = Hendelsesdata.lagDeltaker(deltakerliste = deltakerliste)
        val navBruker = Persondata.lagNavBruker()
        val ansvarligNavVeileder = Hendelsesdata.ansvarligNavVeileder()
        val hendelser: List<Hendelse> = listOf(
            Hendelsesdata.hendelse(
                HendelseTypeData.endreAvsluttDeltakelse(harFullfort = false),
                deltaker = deltaker,
                ansvarlig = ansvarligNavVeileder,
                opprettet = LocalDateTime.now().minusMinutes(20),
            ),
        )

        val pdfDto = lagEndringsvedtakPdfDto(
            deltaker = deltaker,
            navBruker = navBruker,
            ansvarlig = ansvarligNavVeileder,
            hendelser = hendelser,
            opprettetDato = LocalDate.now(),
        )

        pdfDto.endringer.size shouldBe 1
        val avsluttDeltakelseResult = (pdfDto.endringer.first() as EndringDto.EndreAvslutning)
        avsluttDeltakelseResult.harFullfort shouldBe "Nei"
    }
}
