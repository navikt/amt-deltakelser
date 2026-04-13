package no.nav.amt.pdfgen

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.journalforing.pdf.InnholdPdfDto
import no.nav.amt.pdfgen.util.RenderUtils.renderSection

class InnholdBddTest :
    BehaviorSpec({
        Given("sub-template innhold") {
            When("innhold rendres med ledetekst og valgte innholdselementer") {
                val innhold =
                    InnholdPdfDto(
                        valgteInnholdselementer =
                            listOf(
                                "Innhold Pt 1",
                                "Innhold Pt 2",
                            ),
                        fritekstBeskrivelse = "",
                        ledetekst = "Avklaring ledetekst",
                    )

                val doc = renderSection("innhold", FakeVedtakContainer(innhold))

                Then("vises heading") {
                    val heading = doc.selectFirst("h2").shouldNotBeNull()
                    heading.text() shouldBe "Dette er innholdet"
                }
            }

            When("deltaker rendres uten innhold") {
                val doc = renderSection("innhold", FakeVedtakContainer(null))

                Then("vises ikke heading") {
                    doc.selectFirst("h2").shouldBeNull()
                }
            }
        }
    }) {
    companion object {
        private data class FakeDeltakerContainer(
            val innhold: InnholdPdfDto?,
        )

        private data class FakeVedtakContainer(
            val deltaker: FakeDeltakerContainer,
        ) {
            constructor(innhold: InnholdPdfDto?) : this(FakeDeltakerContainer(innhold))
        }
    }
}
