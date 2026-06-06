package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.journalforing.pdf.InnholdPdfDto
import no.nav.amt.pdfgen.util.DtoBuilders.innsokingsbrev
import no.nav.amt.pdfgen.util.RenderUtils.render

class InnsokingsbrevTest :
    DescribeSpec({

        describe("Innsøkingsbrev PDF") {

            it("skal rendre med alle obligatoriske felter") {
                val brev = innsokingsbrev()
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Ola Nordmann"
                doc.text() shouldContain "12345678910"
                doc.text() shouldContain "Jobbklubb"
                doc.text() shouldContain "Arrangør AS"
                doc.text() shouldContain "Møtested AS"
                doc.text() shouldContain "Nav Veileder"
                doc.text() shouldContain "Nav Oslo"
            }

            it("skal rendre sidetittel") {
                val brev = innsokingsbrev(tiltakskode = Tiltakskode.JOBBKLUBB)
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Innsøkingsbrev for JOBBKLUBB"
            }

            it("skal rendre ingressnavn") {
                val brev = innsokingsbrev()
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Jobbklubb"
            }

            it("skal rendre ledetekst når innhold finnes") {
                val innhold = InnholdPdfDto(
                    valgteInnholdselementer = emptyList(),
                    fritekstBeskrivelse = null,
                    ledetekst = "Dette er en ledetekst for kurset",
                )
                val brev = innsokingsbrev(innholdPdfDto = innhold)
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Dette er en ledetekst for kurset"
            }

            it("skal ikke rendre ledetekst når innhold mangler") {
                val brev = innsokingsbrev(innholdPdfDto = null)
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldNotContain "Dette er en ledetekst"
            }

            it("skal rendre mellomnavn når tilstede") {
                val brev0 = innsokingsbrev()
                val brev = brev0.copy(
                    deltaker = brev0.deltaker.copy(
                        mellomnavn = "Erik",
                    ),
                )
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Ola Erik Nordmann"
            }

            describe("Oppstartstyper") {

                it("FELLES - skal vise felles-spesifikk tekst og startdato") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.FELLES)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "Når det nærmer seg oppstart av kurset"
                    doc.text() shouldContain "Kurset starter"
                }

                it("LOPENDE - skal rendre løpende-spesifikk tekst") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.LOPENDE)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "Nav vurderer søknaden din"
                    doc.text() shouldNotContain "Når det nærmer seg oppstart av kurset"
                }

                it("ENKELTPLASS - skal ikke vise felles-/løpende-tekst eller startdato") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.ENKELTPLASS)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "Ola Nordmann"
                    doc.text() shouldNotContain "Når det nærmer seg oppstart av kurset"
                    doc.text() shouldNotContain "Nav vurderer søknaden din"
                    doc.text() shouldNotContain "Kurset starter"
                }
            }

            describe("Tiltakstyper") {

                it("JOBBKLUBB - skal ikke vise kontakt-fra-arrangør-tekst") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.JOBBKLUBB)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldNotContain "For å avgjøre hvem som skal få plass"
                }

                it("AVKLARING - skal vise kontakt-fra-arrangør-tekst") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.AVKLARING)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "For å avgjøre hvem som skal få plass"
                }

                it("GRUPPE_ARBEIDSMARKEDSOPPLAERING - skal vise kontakt-fra-arrangør-tekst") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "For å avgjøre hvem som skal få plass"
                }
            }
        }
    })
