package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.lib.models.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.enkeltplassInnsokingsbrev
import no.nav.amt.pdfgen.util.RenderUtils.render

class EnkeltplassInnsokingsbrevTest :
    DescribeSpec({

        describe("Enkeltplass innsøkingsbrev PDF") {

            it("skal rendre med alle obligatoriske felter") {
                val brev = enkeltplassInnsokingsbrev(
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)

                doc.text() shouldContain "Ola Erik Nordmann"
                doc.text() shouldContain "Arbeidsforberedende trening"
                doc.text() shouldContain "Jada Fangst AS"
                doc.text() shouldContain "Du er søkt inn og Nav vurderer søknaden din"
            }

            describe("Prisformatering") {

                it("skal formatere pris med tusenerskilletegn for Anskaffelse") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Anskaffelse(
                            pris = 125000,
                        ),
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "125 000"
                    doc.text() shouldContain "Nav har kjøpt en plass hos opplæringsstedet"
                    doc.text() shouldContain "Nav betaler for opplæringen"
                }

                it("skal formatere tilskuddspris med tusenerskilletegn") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd(
                            tilleggsopplysninger = null,
                            tilskudd = listOf(
                                EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                    type = "Skolepenger",
                                    pris = 75000,
                                ),
                                EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                    type = "Semesteravgift",
                                    pris = 50000,
                                ),
                            ),
                        ),
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "125 000"
                }

                it("skal vise IngenKostnader tekst") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Du eller Nav skal ikke betale for opplæringen"
                }

                it("skal vise Innbyggerfinansiert tekst med tilleggsopplysninger") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Innbyggerfinansiert(
                            tilleggsopplysninger = "Du må betale for gjennomføring av kurset",
                        ),
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Du må selv betale for opplæringen"
                    doc.text() shouldContain "Du må betale for gjennomføring av kurset"
                }
            }

            describe("Innholdstyper") {

                it("Arbeidsmarkedsopplaering - skal vise bransje og sertifiseringer") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                            bransje = "Helse og omsorg",
                            forerkortOgSertifiseringer = listOf(
                                "D1 - Minibuss",
                                "Førstehjelpskurs",
                                "Beskrivelse av sårbehandling",
                            ),
                        ),
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Bransje: Helse og omsorg"
                    doc.text() shouldContain "Førerkort og sertifisering:"
                    doc.text() shouldContain "D1 - Minibuss"
                    doc.text() shouldContain "Førstehjelpskurs"
                    doc.text() shouldContain "Beskrivelse av sårbehandling"
                }

                it("Arbeidsmarkedsopplaering - skal ikke vise sertifiseringer når listen er tom") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                            bransje = "Helse og omsorg",
                            forerkortOgSertifiseringer = emptyList(),
                        ),
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Bransje: Helse og omsorg"
                    doc.text() shouldNotContain "Førerkort og sertifisering:"
                }

                it("FagOgYrkesopplaering - skal vise utdanningsprogram og lærefag") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.FagOgYrkesopplaering(
                            utdanningsprogram = "Elektro- og datateknologi",
                            laerefag = listOf(
                                "Avionikerfaget",
                                "Droneoperatør",
                            ),
                        ),
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Utdanningsprogram: Elektro- og datateknologi"
                    doc.text() shouldContain "Lærefag:"
                    doc.text() shouldContain "Avionikerfaget"
                    doc.text() shouldContain "Droneoperatør"
                }

                it("UtenInnhold - skal ikke vise innholdsdetaljer") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldNotContain "Bransje:"
                    doc.text() shouldNotContain "Utdanningsprogram:"
                    doc.text() shouldNotContain "Lærefag:"
                    doc.text() shouldNotContain "Førerkort og sertifisering:"
                }
            }

            describe("Tilskudd") {

                it("skal vise liste med individuelle tilskudd og totalpris") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd(
                            tilleggsopplysninger = null,
                            tilskudd = listOf(
                                EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                    type = "Skolepenger",
                                    pris = 75000,
                                ),
                                EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                    type = "Semesteravgift",
                                    pris = 50000,
                                ),
                            ),
                        ),
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Du kan få tilskudd til å dekke disse utgiftene:"
                    doc.text() shouldContain "Skolepenger: 75 000"
                    doc.text() shouldContain "Semesteravgift: 50 000"
                    doc.text() shouldContain "Totalt anslått tilskudd: 125 000 kroner"
                    doc.text() shouldContain "Utbetaling skjer når utgiftene er dokumentert"
                }

                it("skal vise tilleggsopplysninger når tilstede") {
                    val brev = enkeltplassInnsokingsbrev(
                        innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                        prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd(
                            tilleggsopplysninger = "Dokumentasjon må leveres innen 3 måneder",
                            tilskudd = listOf(
                                EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                    type = "Skolepenger",
                                    pris = 10000,
                                ),
                            ),
                        ),
                    )
                    val doc = render("enkeltplass-innsokingsbrev", brev)

                    doc.text() shouldContain "Dokumentasjon må leveres innen 3 måneder"
                }
            }

            it("skal vise deltakelsesmengde") {
                val brev = enkeltplassInnsokingsbrev(
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)

                doc.text() shouldContain "Deltakelsesmengde"
                doc.text() shouldContain "5 dager i uka"
            }

            it("skal vise startdato og sluttdato") {
                val brev = enkeltplassInnsokingsbrev(
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)

                doc.text() shouldContain "Opplæringen starter"
                doc.text() shouldContain "og slutter"
            }

            it("skal vise link til tilleggsstønader") {
                val brev = enkeltplassInnsokingsbrev(
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.IngenKostnader,
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)

                doc.text() shouldContain "Les mer om støtte til andre utgifter"
                doc.text() shouldContain "nav.no/tilleggsstønader"
            }
        }
    })
