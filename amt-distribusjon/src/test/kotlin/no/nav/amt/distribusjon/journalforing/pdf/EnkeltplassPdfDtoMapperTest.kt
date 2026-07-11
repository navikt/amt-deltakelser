package no.nav.amt.distribusjon.journalforing.pdf

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.distribusjon.journalforing.pdf.EnkeltplassPdfDtoMapper.tiltakskodenavn
import no.nav.amt.distribusjon.journalforing.pdf.EnkeltplassPdfDtoMapper.toInnhold
import no.nav.amt.distribusjon.journalforing.pdf.EnkeltplassPdfDtoMapper.toPrisinformasjon
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EnkeltplassPdfDtoMapperTest {
    @Nested
    inner class TiltakskodenavnTests {
        @Test
        fun `tiltakskodenavn skal returnere riktig navn for ARBEIDSMARKEDSOPPLAERING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Arbeidsmarkedsopplæring"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for STUDIESPESIALISERING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.STUDIESPESIALISERING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Studiespesialisering"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for FAG_OG_YRKESOPPLAERING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.FAG_OG_YRKESOPPLAERING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Fag- og yrkesopplæring"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for HOYERE_YRKESFAGLIG_UTDANNING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Høyere yrkesfaglig utdanning"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Arbeidsmarkedsopplæring (enkeltplass)"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for ENKELTPLASS_FAG_OG_YRKESOPPLAERING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Fag- og yrkesopplæring (enkeltplass)"
        }

        @Test
        fun `tiltakskodenavn skal returnere riktig navn for HOYERE_UTDANNING`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.HOYERE_UTDANNING),
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Høyere utdanning"
        }

        @Test
        fun `tiltakskodenavn skal returnere kurstype for NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                        valg = mapOf(UUID.randomUUID() to "Norskopplæring"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV),
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.tiltakskodenavn()

            resultat shouldBe "Norskopplæring"
        }

        @Test
        fun `tiltakskodenavn skal kaste exception når NORSKOPPLAERING mangler kurstype`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV),
                opplaringKategoriseringValg = null,
            )

            shouldThrow<IllegalStateException> {
                deltakerliste.tiltakskodenavn()
            }
        }

        @Test
        fun `tiltakskodenavn skal kaste exception for ukjent tiltakskode`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                tiltak = Hendelsesdata.tiltak(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
            )

            shouldThrow<IllegalArgumentException> {
                deltakerliste.tiltakskodenavn()
            }
        }
    }

    @Nested
    inner class ToInnholdTests {
        @Test
        fun `toInnhold skal returnere UtenInnhold når KURSTYPE_ID finnes`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                        valg = mapOf(UUID.randomUUID() to "Norskprøve"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.toInnhold()

            resultat shouldBe EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold
        }

        @Test
        fun `toInnhold skal returnere Arbeidsmarkedsopplaering når BRANSJE_ID finnes`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Elektrikk"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.toInnhold()

            resultat as no.nav.amt.internapi.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering>()
            resultat.bransje shouldBe "Elektrikk"
            resultat.forerkortOgSertifiseringer shouldBe emptyList()
        }

        @Test
        fun `toInnhold skal inkludere forerkort og sertifiseringer i Arbeidsmarkedsopplaering`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Elektrikk"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        valg = mapOf(UUID.randomUUID() to "CE", UUID.randomUUID() to "D"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.SERTIFISERINGER,
                        valg = mapOf(UUID.randomUUID() to "Sikkerhetskurs"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.toInnhold()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering>()
            resultat.bransje shouldBe "Elektrikk"
            resultat.forerkortOgSertifiseringer.size shouldBe 3
        }

        @Test
        fun `toInnhold skal returnere FagOgYrkesopplaering når UTDANNINGSPROGRAM_ID finnes`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                        valg = mapOf(UUID.randomUUID() to "Helse- og oppvekstfag"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.LAREFAG,
                        valg = mapOf(UUID.randomUUID() to "Elektorfaget"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.toInnhold()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.FagOgYrkesopplaering>()
            resultat.utdanningsprogram shouldBe "Helse- og oppvekstfag"
            resultat.laerefag shouldBe listOf("Elektorfaget")
        }

        @Test
        fun `toInnhold skal kaste IllegalStateException når opplaringKategoriseringValg er null`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = null,
            )

            shouldThrow<IllegalStateException> {
                deltakerliste.toInnhold()
            }
        }

        @Test
        fun `toInnhold skal kaste IllegalArgumentException når ingen gyldig kategoriseringstype finnes`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.INNHOLDSELEMENTER,
                        valg = mapOf(UUID.randomUUID() to "Noen innholdselemet"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            shouldThrow<IllegalArgumentException> {
                deltakerliste.toInnhold()
            }
        }

        @Test
        fun `toInnhold skal kaste IllegalArgumentException når BRANSJE_ID har tomme verdier`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = emptyMap(),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            shouldThrow<IllegalArgumentException> {
                deltakerliste.toInnhold()
            }
        }

        @Test
        fun `toInnhold skal kaste IllegalArgumentException når UTDANNINGSPROGRAM_ID har tomme verdier`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                        valg = emptyMap(),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            shouldThrow<IllegalArgumentException> {
                deltakerliste.toInnhold()
            }
        }

        @Test
        fun `toInnhold skal prioritere KURSTYPE_ID over andre kategoriseringer`() {
            val opplaringKategoriseringValg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                        valg = mapOf(UUID.randomUUID() to "Norskprøve"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Elektrikk"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                opplaringKategoriseringValg = opplaringKategoriseringValg,
            )

            val resultat = deltakerliste.toInnhold()

            resultat shouldBe EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold
        }
    }

    @Nested
    inner class ToPrisinformasjonTests {
        @Test
        fun `toPrisinformasjon skal returnere Anskaffelse når prisinformasjon er Anskaffelse`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 50000),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Anskaffelse>()
            resultat.pris shouldBe 50000
        }

        @Test
        fun `toPrisinformasjon skal returnere Tilskudd med sortert liste`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.Tilskudd(
                    tilskudd = listOf(
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
                            pris = 5000,
                        ),
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                            pris = 10000,
                        ),
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
                            pris = 1000,
                        ),
                    ),
                    tilleggsopplysninger = null,
                ),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd>()
            resultat.tilskudd.size shouldBe 3
            resultat.tilskudd[0].type shouldBe "Skolepenger"
            resultat.tilskudd[1].type shouldBe "Eksamensgebyr"
            resultat.tilskudd[2].type shouldBe "Studiereise"
            resultat.tilleggsopplysninger shouldBe null
        }

        @Test
        fun `toPrisinformasjon skal inkludere tilleggsopplysninger i Tilskudd`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.Tilskudd(
                    tilskudd = listOf(
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                            pris = 15000,
                        ),
                    ),
                    tilleggsopplysninger = "Skolepengene dekker lærebøker og materiell",
                ),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd>()
            resultat.tilleggsopplysninger shouldBe "Skolepengene dekker lærebøker og materiell"
        }

        @Test
        fun `toPrisinformasjon skal returnere IngenKostnader når aarsak er OPPLAERINGEN_ER_KOSTNADSFRI`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.IngenKostnader(
                    aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                    tilleggsopplysninger = null,
                ),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat shouldBe EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader
        }

        @Test
        fun `toPrisinformasjon skal returnere Innbyggerfinansiert når aarsak er OPPLAERINGEN_ER_EGENFINANSIERT`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.IngenKostnader(
                    aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                    tilleggsopplysninger = "Deltaker finansierer kurset selv",
                ),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Innbyggerfinansiert>()
            resultat.tilleggsopplysninger shouldBe "Deltaker finansierer kurset selv"
        }

        @Test
        fun `toPrisinformasjon skal kaste IllegalStateException når prisinformasjon er null`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = null,
            )

            shouldThrow<IllegalStateException> {
                deltakerliste.toPrisinformasjon()
            }
        }

        @Test
        fun `toPrisinformasjon skal kaste IllegalStateException når tilleggsopplysninger mangler for egenfinansiert`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.IngenKostnader(
                    aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                    tilleggsopplysninger = null,
                ),
            )

            shouldThrow<IllegalStateException> {
                deltakerliste.toPrisinformasjon()
            }
        }

        @Test
        fun `toPrisinformasjon skal handlere multiple tilskudd typer korrekt`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.Tilskudd(
                    tilskudd = listOf(
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SEMESTERAVGIFT,
                            pris = 2000,
                        ),
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.INTEGRERT_BOTILBUD,
                            pris = 8000,
                        ),
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                            pris = 20000,
                        ),
                    ),
                    tilleggsopplysninger = "Omfattende tilskudd",
                ),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd>()
            resultat.tilskudd.size shouldBe 3
            resultat.tilskudd[0].type shouldBe "Skolepenger"
            resultat.tilskudd[1].type shouldBe "Semesteravgift"
            resultat.tilskudd[2].type shouldBe "Integrert botilbud"
            resultat.tilskudd.sumOf { it.pris } shouldBe 30000
        }

        @Test
        fun `toPrisinformasjon skal håndtere Anskaffelse med liten pris`() {
            val deltakerliste = Hendelsesdata.lagDeltakerliste(
                prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 100),
            )

            val resultat = deltakerliste.toPrisinformasjon()

            resultat.shouldBeInstanceOf<EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Anskaffelse>()
            resultat.pris shouldBe 100
        }
    }
}
