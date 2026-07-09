package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.journalforing.pdf.EnkeltplassInnsokingsbrevPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.enkeltplassInnsokingsbrev
import no.nav.amt.pdfgen.util.RenderUtils.render

class EnkeltplassInnsokingsbrevTest :
    DescribeSpec({

        describe("Enkeltplass innsøkingsbrev PDF") {
            it("Norskopplaering") {
                val brev = enkeltplassInnsokingsbrev(
                    tiltaksnavn = "Norskopplæring",
                    innhold = EnkeltplassInnsokingsbrevPdfDto.EnkeltplassInnhold.UtenInnhold,
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
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
                )
                val doc = render("enkeltplass-innsokingsbrev", brev)
                doc.text() shouldContain "Ola Erik Nordmann"
                // java.io.File("/tmp/enkeltplass-preview.html").writeText(doc.outerHtml())
            }
        }
    })
