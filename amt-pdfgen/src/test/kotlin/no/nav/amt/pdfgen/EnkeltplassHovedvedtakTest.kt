package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.enkeltplassPdfDto
import no.nav.amt.pdfgen.util.RenderUtils.render

class EnkeltplassHovedvedtakTest :
    DescribeSpec({

        describe("Enkeltplass hovedvedtak PDF") {

            it("skal rendre med alle obligatoriske felter") {
                val brev = enkeltplassPdfDto(
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-hovedvedtak", brev)

                doc.text() shouldContain "Ola Erik Nordmann"
                doc.text() shouldContain "Arbeidsforberedende trening"
                doc.text() shouldContain "Jada Fangst AS"
                doc.text() shouldContain "Du har fått godkjent opplæringen"
                doc.select("h2").any { it.text() == "Pris og betalingsbetingelser" } shouldBe true
            }

            it("skal vise vedtak- og klageseksjoner") {
                val brev = enkeltplassPdfDto(
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-hovedvedtak", brev)

                doc.text() shouldContain "Dette er et vedtak"
                doc.text() shouldContain "Du har rett til å klage"
                doc.text() shouldContain "nav.no/klage"
            }

            describe("Prisformatering") {

                it("skal formatere pris med tusenskilletegn for Anskaffelse") {
                    val brev = enkeltplassPdfDto(
                        innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.Anskaffelse(
                            pris = 125000,
                        ),
                    )
                    val doc = render("enkeltplass-hovedvedtak", brev)

                    doc.text() shouldContain "125 000"
                    doc.text() shouldContain "Nav har kjøpt en plass hos opplæringsstedet"
                }

                it("skal vise Innbyggerfinansiert tekst med tilleggsopplysninger") {
                    val brev = enkeltplassPdfDto(
                        innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.Innbyggerfinansiert(
                            tilleggsopplysninger = "Du må betale for gjennomføring av kurset",
                        ),
                    )
                    val doc = render("enkeltplass-hovedvedtak", brev)

                    doc.text() shouldContain "Du må selv betale for opplæringen"
                    doc.text() shouldContain "Du må betale for gjennomføring av kurset"
                }
            }

            describe("Innholdstyper") {

                it("Arbeidsmarkedsopplaering - skal vise bransje og sertifiseringer") {
                    val brev = enkeltplassPdfDto(
                        innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                            bransje = "Helse og omsorg",
                            forerkortOgSertifiseringer = listOf("D1 - Minibuss", "Førstehjelpskurs"),
                        ),
                        prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-hovedvedtak", brev)

                    doc.text() shouldContain "Bransje: Helse og omsorg"
                    doc.text() shouldContain "Førerkort og sertifisering:"
                    doc.text() shouldContain "D1 - Minibuss"
                    doc.text() shouldContain "Førstehjelpskurs"
                }

                it("UtenInnhold - skal ikke vise innholdsdetaljer") {
                    val brev = enkeltplassPdfDto(
                        innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-hovedvedtak", brev)

                    doc.text() shouldNotContain "Bransje:"
                    doc.text() shouldNotContain "Utdanningsprogram:"
                    doc.text() shouldNotContain "Lærefag:"
                }
            }

            it("skal vise deltakelsesmengde") {
                val brev = enkeltplassPdfDto(
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                    deltakelsesmengdeAntallDager = 5,
                )
                val doc = render("enkeltplass-hovedvedtak", brev)

                doc.text() shouldContain "Deltakelsesmengde"
                doc.text() shouldContain "5 dager i uka"
            }

            it("skal ha korrekt metadata i head") {
                val brev = enkeltplassPdfDto(
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-hovedvedtak", brev)

                doc.selectFirst("meta[name=description]")?.attr("content") shouldBe "Vedtak om tiltak"
                doc.selectFirst("meta[name=subject]")?.attr("content") shouldBe "Vedtak"
                doc.selectFirst("title")?.text() shouldBe "Vedtak om tiltaksdeltakelse"
            }
        }
    })
