package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.enkeltplassPdfDto
import no.nav.amt.pdfgen.util.RenderUtils.render

class EnkeltplassInnsokingsbrevRenderTest :
    DescribeSpec({

        describe("Enkeltplass innsøkingsbrev PDF") {
            it("Norskopplaering") {
                val brev = enkeltplassPdfDto(
                    tiltaksnavn = "Norskopplæring",
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.UtenInnhold,
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.Tilskudd(
                        tilleggsopplysninger = null,
                        tilskudd = listOf(
                            EnkeltplassPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                type = "Tilskuddstype 1",
                                pris = 1000,
                            ),
                            EnkeltplassPdfDto.Prisinformasjon.Tilskudd.TilskuddInfo(
                                type = "Tilskuddstype 2",
                                pris = 2000,
                            ),
                        ),
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }

            it("FagOgYrkesopplaering") {
                val brev = enkeltplassPdfDto(
                    tiltaksnavn = "Fag- og yrkesopplæring",
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.FagOgYrkesopplaering(
                        utdanningsprogram = "Elektro og datateknologi",
                        laerefag = listOf(
                            "Avionikerfaget",
                            "Droneoperatør",
                        ),
                    ),
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.Innbyggerfinansiert(
                        tilleggsopplysninger = "Dette er en tilleggsopplysning",
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }

            it("Arbeidsmarkedsopplaering") {
                val brev = enkeltplassPdfDto(
                    tiltaksnavn = "Arbeidsmarkedsopplæring",
                    innhold = EnkeltplassPdfDto.EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                        bransje = "Barne- og ungdomsarbeid",
                        forerkortOgSertifiseringer = listOf(
                            "D1 - Minibuss",
                            "HP QuickTest Professional (QTP)",
                        ),
                    ),
                    prisinformasjon = EnkeltplassPdfDto.Prisinformasjon.Innbyggerfinansiert(
                        tilleggsopplysninger = "Dette er en tilleggsopplysning",
                    ),
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }
        }
    })
