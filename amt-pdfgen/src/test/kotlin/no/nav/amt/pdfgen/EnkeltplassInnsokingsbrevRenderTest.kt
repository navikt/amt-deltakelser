package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.enkeltplassInnsokingsbrev
import no.nav.amt.pdfgen.util.RenderUtils.render

class EnkeltplassInnsokingsbrevRenderTest :
    DescribeSpec({

        describe("Enkeltplass innsøkingsbrev PDF") {
            it("Norskopplaering") {
                val brev = enkeltplassInnsokingsbrev(
                    tiltaksnavn = "Norskopplæring",
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd(
                        tilleggsopplysninger = null,
                        tilskudd = listOf(
                            EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                type = "Tilskuddstype 1",
                                pris = 1000,
                            ),
                            EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                type = "Tilskuddstype 2",
                                pris = 2000,
                            ),
                        ),
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }

            it("FagOgYrkesopplaering") {
                val brev = enkeltplassInnsokingsbrev(
                    tiltaksnavn = "Fag- og yrkesopplæring",
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.FagOgYrkesopplaering(
                        utdanningsprogram = "Elektro og datateknologi",
                        laerefag = listOf(
                            "Avionikerfaget",
                            "Droneoperatør",
                        ),
                    ),
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Innbyggerfinansiert(
                        tilleggsopplysninger = "Dette er en tilleggsopplysning",
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }

            it("Arbeidsmarkedsopplaering") {
                val brev = enkeltplassInnsokingsbrev(
                    tiltaksnavn = "Arbeidsmarkedsopplæring",
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                        bransje = "Barne- og ungdomsarbeid",
                        forerkortOgSertifiseringer = listOf(
                            "D1 - Minibuss",
                            "HP QuickTest Professional (QTP)",
                        ),
                    ),
                    prisinformasjon = EnkeltplassInnsokingsbrevPdfDto.Prisinformasjon.Innbyggerfinansiert(
                        tilleggsopplysninger = "Dette er en tilleggsopplysning",
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }
        }
    })
