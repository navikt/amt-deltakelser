package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.pdfgen.util.DtoBuilders.innsokingsbrev
import no.nav.amt.pdfgen.util.RenderUtils.render

class InnsokingsbrevTest :
    DescribeSpec({

        describe("Innsøkingsbrev PDF") {

            it("skal rendrer med alle obligatoriske felter") {
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

            it("skal rendrer sidetittel") {
                val brev = innsokingsbrev(tiltakskode = Tiltakskode.JOBBKLUBB)
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Innsøkingsbrev for JOBBKLUBB"
            }

            it("skal rendrer ingressnavn") {
                val brev = innsokingsbrev()
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Jobbklubb"
            }

            it("skal rendrer ledetekst") {
                val brev = innsokingsbrev()
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Ola Nordmann"
            }

            it("skal rendrer mellomnavn når tilstede") {
                val brev = innsokingsbrev().copy(
                    deltaker = innsokingsbrev().deltaker.copy(
                        mellomnavn = "Erik",
                    ),
                )
                val doc = render("innsokingsbrev", brev)

                doc.text() shouldContain "Ola Erik Nordmann"
            }

            describe("Oppstartstyper") {

                it("FELLES - skal vise startdato") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.FELLES)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "Kurset starter"
                }

                it("LOPENDE - skal ikke vise ordinær oppstart") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.LOPENDE)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldNotContain "Ordinær oppstart"
                }

                it("ENKELTPLASS - skal rendrer") {
                    val brev = innsokingsbrev(oppstartstype = Oppstartstype.ENKELTPLASS)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "Ola Nordmann"
                }
            }

            describe("Tiltakstyper") {

                it("JOBBKLUBB") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.JOBBKLUBB)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "JOBBKLUBB"
                }

                it("AVKLARING") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.AVKLARING)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "AVKLARING"
                }

                it("GRUPPE_ARBEIDSMARKEDSOPPLAERING") {
                    val brev = innsokingsbrev(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
                    val doc = render("innsokingsbrev", brev)

                    doc.text() shouldContain "GRUPPE_ARBEIDSMARKEDSOPPLAERING"
                }
            }
        }
    })
