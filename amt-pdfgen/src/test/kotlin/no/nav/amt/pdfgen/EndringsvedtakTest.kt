package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.internapi.journalforing.pdf.EndringDto
import no.nav.amt.internapi.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.internapi.journalforing.pdf.ForslagDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.pdfgen.util.DtoBuilders.defaultEndring
import no.nav.amt.pdfgen.util.DtoBuilders.endringsvedtak
import no.nav.amt.pdfgen.util.RenderUtils.fixedDate
import no.nav.amt.pdfgen.util.RenderUtils.render
import no.nav.amt.pdfgen.util.RenderUtils.toNorwegianShortDate
import org.jsoup.nodes.Document

class EndringsvedtakTest :
    DescribeSpec({

        describe("Endringsvedtak PDF") {

            describe("Oppstart") {
                it("viser tekst for DIREKTE_VEDTAK oppstart") {
                    val doc = renderEndringsvedtak(endringsvedtak(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK))
                    doc.text() shouldContain "Du ble meldt på arbeidsmarkedstiltaket"
                }

                it("viser tekst for TRENGER_GODKJENNING oppstart") {
                    val doc =
                        renderEndringsvedtak(endringsvedtak(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING))
                    doc.text() shouldContain "Du ble søkt inn på arbeidsmarkedstiltaket"
                }
            }

            describe("Klagerett") {
                it("viser klagerett når true") {
                    val doc = renderEndringsvedtak(endringsvedtak(klagerett = true))
                    doc.text() shouldContain "Du har rett til å klage"
                }

                it("viser unntak når klagerett er false") {
                    val doc = renderEndringsvedtak(endringsvedtak(klagerett = false))
                    doc.text() shouldNotContain "Du har rett til å klage"
                }
            }

            describe("Endringstyper") {
                describe("pavirkerPris-tekst") {
                    val tekst =
                        "Endringen forutsetter at endring i pris eller betalingsbetingelser blir godkjent. Du vil få en egen beskjed om dette."

                    it("viser teksten når pavirkerPris er true på endringstype som støtter feltet") {
                        val doc = renderEndringsvedtak(
                            endringsvedtak(
                                endringer = listOf(
                                    EndringDto.EndreStartdato(
                                        begrunnelseFraNav = "Begrunnelse",
                                        forslagFraArrangor = null,
                                        pavirkerPris = true,
                                        tittel = "Startdato tittel",
                                    ),
                                ),
                            ),
                        )

                        doc.text() shouldContain tekst
                    }

                    it("viser ikke teksten når pavirkerPris er false på endringstype som støtter feltet") {
                        val doc = renderEndringsvedtak(
                            endringsvedtak(
                                endringer = listOf(
                                    EndringDto.EndreStartdato(
                                        begrunnelseFraNav = "Begrunnelse",
                                        forslagFraArrangor = null,
                                        pavirkerPris = false,
                                        tittel = "Startdato tittel",
                                    ),
                                ),
                            ),
                        )

                        doc.text() shouldNotContain tekst
                    }
                }

                it("viser begrunnelse og prisinformasjon for GodkjennPrisendring") {
                    val doc = renderEndringsvedtak(
                        endringsvedtak(
                            endringer = listOf(
                                EndringDto.GodkjennPrisendring(
                                    tittel = "Pris og betalingsbetingelser er endret",
                                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader(
                                        "~tilleggsopplysninger~",
                                    ),
                                    begrunnelseFraNav = "~begrunnelseFraNav~",
                                ),
                            ),
                        ),
                    )

                    doc.select("section h2").eachText() shouldNotContain "Pris og betalingsbetingelser"
                    doc.text() shouldContain "Pris og betalingsbetingelser er endret"
                    doc.text() shouldContain "Nav skal ikke betale for opplæringen."
                    doc.select("p.margins-1-rem").eachText() shouldContain "~tilleggsopplysninger~"
                    doc.text().substringAfter("~tilleggsopplysninger~") shouldContain "Nav sin begrunnelse: ~begrunnelseFraNav~"
                }

                describe("erEnkeltplassinnhold") {
                    it("viser enkeltplass-innhold og skjuler standard innholdsliste når flagget er true") {
                        val doc = renderEndringsvedtak(
                            endringsvedtak(
                                endringer =
                                    listOf(
                                        EndringDto.EnkeltplassEndreOpplaringKategorisering(
                                            tittel = "Innholdet er endret",
                                            innholdFritekst = "Fritekst om hva opplæringen inneholder",
                                            innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                                                bransje = "Bygg og anlegg",
                                                forerkortOgSertifiseringer = listOf("B - Personbil"),
                                            ),
                                            erEnkeltplassinnhold = true,
                                            pavirkerPris = false,
                                        ),
                                    ),
                            ),
                        )

                        doc.text() shouldContain "Fritekst om hva opplæringen inneholder"
                        doc.text() shouldContain "Bransje: Bygg og anlegg"
                        doc.text() shouldNotContain "Dette er ledeteksten"
                        doc.text() shouldNotContain "Kurstype:"
                    }

                    it("viser kurstype i innholdsseksjonen før friteksten") {
                        // Arrange
                        val kurstyper = listOf(
                            "Norskopplæring",
                            "Grunnleggende ferdigheter",
                            "Forberedende opplæring for voksne",
                        )

                        kurstyper.forEach { kurstype ->
                            // Arrange
                            val payload =
                                endringsvedtak(
                                    endringer =
                                        listOf(
                                            EndringDto.EnkeltplassEndreOpplaringKategorisering(
                                                tittel = "Innholdet er endret",
                                                innholdFritekst = "Fritekst om hva opplæringen inneholder",
                                                innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Kurstype(kurstype),
                                                erEnkeltplassinnhold = true,
                                                pavirkerPris = false,
                                            ),
                                        ),
                                )

                            // Act
                            val doc = renderEndringsvedtak(payload)

                            // Assert
                            val section = doc.select("section").first {
                                it.selectFirst("h2")?.text() == "Innholdet er endret"
                            }
                            val sectionText = section.text()

                            section.select("p").eachText() shouldContain "Kurstype: $kurstype"
                            val headingIndex = sectionText.indexOf("Innholdet er endret")
                            val kurstypeIndex = sectionText.indexOf("Kurstype: $kurstype")
                            val fritekstIndex = sectionText.indexOf("Fritekst om hva opplæringen inneholder")
                            (headingIndex < kurstypeIndex) shouldBe true
                            (kurstypeIndex < fritekstIndex) shouldBe true
                        }
                    }

                    it("HTML-escaper kurstype") {
                        // Arrange
                        val kurstype = "Norskopplæring & <språk>"
                        val payload =
                            endringsvedtak(
                                endringer =
                                    listOf(
                                        EndringDto.EnkeltplassEndreOpplaringKategorisering(
                                            tittel = "Innholdet er endret",
                                            innholdFritekst = "Fritekst",
                                            innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Kurstype(kurstype),
                                            erEnkeltplassinnhold = true,
                                            pavirkerPris = false,
                                        ),
                                    ),
                            )

                        // Act
                        val doc = renderEndringsvedtak(payload)

                        // Assert
                        val section = doc.select("section").first {
                            it.selectFirst("h2")?.text() == "Innholdet er endret"
                        }
                        section.select("p").eachText() shouldContain "Kurstype: $kurstype"
                        section.select("p *").size shouldBe 0
                    }

                    it("viser standard innholdsliste når flagget ikke er med i data") {
                        val doc = renderEndringsvedtak(
                            endringsvedtak(
                                endringer =
                                    listOf(
                                        EndringDto.EndreInnhold(
                                            innhold = listOf("Innhold 1", "Innhold 2"),
                                            innholdBeskrivelse = null,
                                            tittel = "Innholdet er endret",
                                        ),
                                    ),
                            ),
                        )

                        doc.text() shouldContain "Innhold 1"
                        doc.text() shouldContain "Innhold 2"
                    }
                }

                describe("Hovedtittel") {
                    it("viser hovedtittel for GodkjennPrisendring") {
                        assertHovedtittel(
                            EndringDto.GodkjennPrisendring(
                                tittel = "Pris og betalingsbetingelser er endret",
                                prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader(
                                    "~tilleggsopplysninger~",
                                ),
                                begrunnelseFraNav = "~begrunnelse~",
                            ),
                        )
                    }

                    it("viser hovedtittel for EndreDeltakelsesmengde") {
                        assertHovedtittel(defaultEndring())
                    }

                    it("viser hovedtittel for EndreStartdato") {
                        assertHovedtittel(
                            EndringDto.EndreStartdato(
                                begrunnelseFraNav = "Begrunnelse",
                                forslagFraArrangor = null,
                                tittel = "Startdato tittel",
                                pavirkerPris = false,
                            ),
                        )
                    }

                    it("viser hovedtittel for EndreSluttdato") {
                        assertHovedtittel(
                            EndringDto.EndreSluttdato(
                                begrunnelseFraNav = "Begrunnelse",
                                forslagFraArrangor = null,
                                tittel = "Sluttdato tittel",
                            ),
                        )
                    }

                    it("viser hovedtittel for ForlengDeltakelse") {
                        assertHovedtittel(
                            EndringDto.ForlengDeltakelse(
                                begrunnelseFraNav = "Begrunnelse",
                                forslagFraArrangor = null,
                                tittel = "Tittel",
                                pavirkerPris = false,
                            ),
                        )
                    }

                    it("viser hovedtittel for IkkeAktuell") {
                        assertHovedtittel(
                            EndringDto.IkkeAktuell(
                                aarsak = "Årsak",
                                begrunnelseFraNav = "BegrunnelseFraNav",
                                forslagFraArrangor = null,
                            ),
                        )
                    }

                    it("viser hovedtittel for AvsluttDeltakelse") {
                        assertHovedtittel(
                            EndringDto.AvsluttDeltakelse(
                                aarsak = "Årsak",
                                begrunnelseFraNav = "BegrunnelseFraNav",
                                forslagFraArrangor = null,
                                harDeltatt = "Ja",
                                harFullfort = "Nei",
                                tittel = "Tittel",
                            ),
                        )
                    }

                    it("viser hovedtittel for EndreAvslutning") {
                        assertHovedtittel(
                            EndringDto.EndreAvslutning(
                                aarsak = "Årsak",
                                begrunnelseFraNav = "BegrunnelseFraNav",
                                forslagFraArrangor = null,
                                harFullfort = "Ja",
                                sluttdato = "2024-01-01",
                                tittel = "Tittel",
                            ),
                        )
                    }

                    it("viser hovedtittel for AvbrytDeltakelse") {
                        assertHovedtittel(
                            EndringDto.AvbrytDeltakelse(
                                aarsak = "Årsak",
                                begrunnelseFraNav = "BegrunnelseFraNav",
                                forslagFraArrangor = null,
                                harDeltatt = "Ja",
                                harFullfort = "Nei",
                                tittel = "Tittel",
                            ),
                        )
                    }

                    it("viser hovedtittel for EndreBakgrunnsinformasjon") {
                        assertHovedtittel(
                            EndringDto.EndreBakgrunnsinformasjon(
                                bakgrunnsinformasjon = "Bakgrunnstekst",
                            ),
                        )
                    }

                    it("viser hovedtittel for EndreInnhold") {
                        assertHovedtittel(
                            EndringDto.EndreInnhold(
                                innhold = listOf("Punkt1", "Punkt2"),
                                innholdBeskrivelse = "Beskrivelse",
                            ),
                        )
                    }
                }

                describe("Forslag fra arrangør") {
                    it("viser forslag om EndreDeltakelsesmengde") {
                        val endring = defaultEndring().copy(
                            forslagFraArrangor = ForslagDto.EndreDeltakelsesmengde(
                                deltakelsesmengdeTekst = "50%",
                                begrunnelseFraArrangor = "Redusert deltakelse",
                            ),
                        )

                        val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))

                        doc.text() shouldContain "Forslaget sendt fra arrangør"
                        doc.text() shouldContain "50%"
                        doc.text() shouldContain "Redusert deltakelse"
                    }

                    it("viser forslag om EndreStartdato") {
                        val startdato = fixedDate.plusDays(5)
                        val endring = EndringDto.EndreStartdato(
                            begrunnelseFraNav = "Begrunnelse",
                            forslagFraArrangor = ForslagDto.EndreStartdato(
                                startdato = startdato,
                                begrunnelseFraArrangor = "Ny start",
                            ),
                            tittel = "Startdato tittel",
                            pavirkerPris = false,
                        )

                        val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))

                        doc.text() shouldContain "Forslaget sendt fra arrangør"
                        doc.text() shouldContain startdato.toNorwegianShortDate()
                        doc.text() shouldContain "Ny start"
                    }

                    it("viser forslag om EndreSluttdato") {
                        val sluttdato = fixedDate.plusMonths(1)
                        val endring = EndringDto.EndreSluttdato(
                            begrunnelseFraNav = "Begrunnelse",
                            forslagFraArrangor = ForslagDto.EndreSluttdato(
                                sluttdato = sluttdato,
                                begrunnelseFraArrangor = "Ny sluttdato",
                            ),
                            tittel = "Sluttdato tittel",
                        )

                        val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))

                        doc.text() shouldContain "Forslaget sendt fra arrangør"
                        doc.text() shouldContain sluttdato.toNorwegianShortDate()
                        doc.text() shouldContain "Ny sluttdato"
                    }

                    it("viser forslag om ForlengDeltakelse") {
                        val sluttdato = fixedDate.plusMonths(2)
                        val endring = EndringDto.ForlengDeltakelse(
                            begrunnelseFraNav = "Begrunnelse",
                            forslagFraArrangor = ForslagDto.ForlengDeltakelse(
                                sluttdato = sluttdato,
                                begrunnelseFraArrangor = "Forlenget",
                            ),
                            tittel = "Tittel",
                            pavirkerPris = false,
                        )

                        val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))

                        doc.text() shouldContain "Forslaget sendt fra arrangør"
                        doc.text() shouldContain sluttdato.toNorwegianShortDate()
                        doc.text() shouldContain "Forlenget"
                    }

                    it("viser forslag om AvsluttDeltakelse") {
                        val sluttdato = fixedDate.plusMonths(1)
                        val endring = EndringDto.AvsluttDeltakelse(
                            aarsak = "Årsak",
                            begrunnelseFraNav = "BegrunnelseFraNav",
                            forslagFraArrangor = ForslagDto.AvsluttDeltakelse(
                                aarsak = "Sykdom",
                                sluttdato = sluttdato,
                                harDeltatt = "Ja",
                                harFullfort = "Nei",
                                begrunnelseFraArrangor = "Anbefalt avslutning",
                            ),
                            harDeltatt = "Ja",
                            harFullfort = "Nei",
                            tittel = "Tittel",
                        )

                        val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))

                        doc.text() shouldContain "Forslaget sendt fra arrangør"
                        doc.text() shouldContain "Sykdom"
                        doc.text() shouldContain sluttdato.toNorwegianShortDate()
                        doc.text() shouldContain "Ja"
                        doc.text() shouldContain "Nei"
                        doc.text() shouldContain "Anbefalt avslutning"
                    }
                }
            }
        }
    }) {
    private companion object {
        fun assertHovedtittel(endring: EndringDto) {
            val doc = renderEndringsvedtak(endringsvedtak(listOf(endring)))
            doc.selectFirst("h1").shouldNotBeNull().text() shouldBe "Endring: Endring i tiltak"
        }

        fun renderEndringsvedtak(payload: EndringsvedtakPdfDto): Document = render("endringsvedtak", payload)
    }
}
