package no.nav.amt.pdfgen

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.journalforing.pdf.Forskriftskapittel
import no.nav.amt.pdfgen.util.DtoBuilders.endringsvedtak
import no.nav.amt.pdfgen.util.DtoBuilders.hovedvedtak
import no.nav.amt.pdfgen.util.DtoBuilders.hovedvedtakVedTildeltPlass
import no.nav.amt.pdfgen.util.RenderUtils.render

class ForskriftskapittelRenderTest :
    DescribeSpec({

        val objectMapper = ObjectMapper()

        describe("JSON-serialisering av Forskriftskapittel") {
            it("skal serialisere KAPITTEL_2 som '2'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_2)
                json shouldBe "\"2\""
            }

            it("skal serialisere KAPITTEL_4 som '4'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_4)
                json shouldBe "\"4\""
            }

            it("skal serialisere KAPITTEL_7 som '7'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_7)
                json shouldBe "\"7\""
            }

            it("skal serialisere KAPITTEL_12 som '12'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_12)
                json shouldBe "\"12\""
            }

            it("skal serialisere KAPITTEL_13 som '13'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_13)
                json shouldBe "\"13\""
            }

            it("skal serialisere KAPITTEL_14 som '14'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_14)
                json shouldBe "\"14\""
            }

            it("skal serialisere KAPITTEL_14A som '14A'") {
                val json = objectMapper.writeValueAsString(Forskriftskapittel.KAPITTEL_14A)
                json shouldBe "\"14A\""
            }
        }

        describe("Rendering av Forskriftskapittel") {
            it("skal rendrer KAPITTEL_2 som '2'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_2)
            }

            it("skal rendrer KAPITTEL_4 som '4'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_4)
            }

            it("skal rendrer KAPITTEL_7 som '7'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_7)
            }

            it("skal rendrer KAPITTEL_12 som '12'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_12)
            }

            it("skal rendrer KAPITTEL_13 som '13'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_13)
            }

            it("skal rendrer KAPITTEL_14 som '14'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_14)
            }

            it("skal rendrer KAPITTEL_14A som '14A'") {
                verifiserForskriftskapittelRendering(Forskriftskapittel.KAPITTEL_14A)
            }
        }

        describe("Hovedvedtak med Forskriftskapittel") {
            Forskriftskapittel.entries.forEach { kapittel ->
                it("skal rendrer hovedvedtak med $kapittel") {
                    val vedtak = hovedvedtak(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
                    val vedtakWithKapittel = vedtak.copy(
                        deltakerliste = vedtak.deltakerliste.copy(
                            forskriftskapittel = kapittel,
                        ),
                    )
                    val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtakWithKapittel)

                    doc.text() shouldContain "forskrift om arbeidsmarkedstiltak kapittel ${kapittel.verdi}"
                }
            }
        }

        describe("Endringsvedtak med Forskriftskapittel") {
            Forskriftskapittel.entries.forEach { kapittel ->
                it("skal rendrer endringsvedtak med $kapittel") {
                    val vedtak = endringsvedtak()
                    val vedtakWithKapittel = vedtak.copy(
                        deltakerliste = vedtak.deltakerliste.copy(
                            forskriftskapittel = kapittel,
                        ),
                    )
                    val doc = render("endringsvedtak", vedtakWithKapittel)

                    doc.text() shouldContain "forskrift om arbeidsmarkedstiltak kapittel ${kapittel.verdi}"
                }
            }
        }

        describe("Hovedvedtak ved tildelt plass med Forskriftskapittel") {
            Forskriftskapittel.entries.forEach { kapittel ->
                it("skal rendrer hovedvedtak ved tildelt plass med $kapittel") {
                    val vedtak = hovedvedtakVedTildeltPlass(forskriftskapittel = kapittel)
                    val doc = render("hovedvedtak-tildelt-plass-felles-oppstart", vedtak)

                    doc.text() shouldContain "forskrift om arbeidsmarkedstiltak kapittel ${kapittel.verdi}"
                }
            }
        }
    })

private fun verifiserForskriftskapittelRendering(kapittel: Forskriftskapittel) {
    val vedtak = endringsvedtak(forskriftskapittel = kapittel)

    val doc = render("endringsvedtak", vedtak)

    doc.text() shouldContain "kapittel ${kapittel.verdi}"
}
