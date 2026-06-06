package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.pdfgen.util.DtoBuilders.hovedvedtakVedTildeltPlass
import no.nav.amt.pdfgen.util.RenderUtils.render

class HovedvedtakVedTildeltPlassTest :
    DescribeSpec({

        describe("Hovedvedtak ved tildelt plass - Felles oppstart") {
            it("skal rendrer med alle obligatoriske felter") {
                val vedtak = hovedvedtakVedTildeltPlass(
                    oppstartstype = Oppstartstype.FELLES,
                )
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Ola Nordmann"
                doc.text() shouldContain "12345678910"
                doc.text() shouldContain "Tiltaksliste"
                doc.text() shouldContain "Arrangør AS"
                doc.text() shouldContain "Her og der"
                doc.text() shouldContain "Nav Saksbehandler"
                doc.text() shouldContain "Nav Oslo"
            }

            it("skal rendrer kursets starttidspunkt") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES)
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Kurset starter"
            }

            it("skal rendrer klagerett når aktivert") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES)
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Du har rett til å klage"
            }

            it("skal ikke vise avtale-kontakt-tekst når kurset ikke har startet") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES, harKursetStartet = false)
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldNotContain "Nav eller arrangøren tar kontakt med deg for å avtale når du skal begynne"
            }

            it("skal vise avtale-kontakt-tekst når kurset har startet") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES, harKursetStartet = true)
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Nav eller arrangøren tar kontakt med deg for å avtale når du skal begynne"
            }

            it("skal rendrer mellomnavn når tilstede") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES).copy(
                    deltaker = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.FELLES).deltaker.copy(
                        mellomnavn = "Erik",
                    ),
                )
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Ola Erik Nordmann"
            }

            describe("Tiltakstyper") {
                it("GRUPPE_ARBEIDSMARKEDSOPPLAERING") {
                    val vedtak = hovedvedtakVedTildeltPlass(
                        tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                        oppstartstype = Oppstartstype.FELLES,
                    )
                    val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)
                    doc.text() shouldContain "Arbeidsforberedende trening"
                }

                it("AVKLARING") {
                    val vedtak = hovedvedtakVedTildeltPlass(
                        tiltakskode = Tiltakskode.AVKLARING,
                        oppstartstype = Oppstartstype.FELLES,
                    )
                    val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)
                    doc.text() shouldContain "Ola Nordmann"
                }
            }
        }

        describe("Hovedvedtak ved tildelt plass - Løpende oppstart") {
            it("skal rendrer med alle obligatoriske felter") {
                val vedtak = hovedvedtakVedTildeltPlass(
                    oppstartstype = Oppstartstype.LOPENDE,
                )
                val doc = render("hovedvedtak-tildelt-plass-loepende-oppstart", vedtak)

                doc.text() shouldContain "Ola Nordmann"
                doc.text() shouldContain "12345678910"
                doc.text() shouldContain "Tiltaksliste"
                doc.text() shouldContain "Arrangør AS"
                doc.text() shouldContain "Her og der"
                doc.text() shouldContain "Nav Saksbehandler"
                doc.text() shouldContain "Nav Oslo"
            }

            it("skal ikke vise kursets starttidspunkt for løpende oppstart") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.LOPENDE)
                val doc = render("hovedvedtak-tildelt-plass-loepende-oppstart", vedtak)

                doc.text() shouldNotContain "Kurset starter"
            }

            it("skal rendrer klagerett når aktivert") {
                val vedtak = hovedvedtakVedTildeltPlass(oppstartstype = Oppstartstype.LOPENDE)
                val doc = render("hovedvedtak-tildelt-plass-loepende-oppstart", vedtak)

                doc.text() shouldContain "Du har rett til å klage"
            }
        }

        describe("Hovedvedtak ved tildelt plass - Enkeltplass oppstart") {
            it("skal rendrer med alle obligatoriske felter") {
                val vedtak = hovedvedtakVedTildeltPlass(
                    oppstartstype = Oppstartstype.ENKELTPLASS,
                )
                val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                doc.text() shouldContain "Ola Nordmann"
                doc.text() shouldContain "Tiltaksliste"
            }
        }
    })
