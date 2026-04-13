package no.nav.amt.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.journalforing.pdf.ArrangorDto
import no.nav.amt.lib.models.journalforing.pdf.AvsenderDto
import no.nav.amt.lib.models.journalforing.pdf.VentelistebrevPdfDto
import no.nav.amt.pdfgen.util.RenderUtils.render
import org.jsoup.nodes.Document
import java.time.LocalDate

class VentelisteTest :
    DescribeSpec({

        describe("Venteliste PDF") {
            it("FELLES oppstart - Skal vise deltakerliste data") {

                val doc =
                    renderVentelistebrev(
                        baseDto(
                            oppstartstype = Oppstartstype.FELLES,
                        ),
                    )
                doc.text() shouldContain "Du er satt på venteliste"
                doc.text() shouldContain "Kurset starter"
            }
            it("LOPENDE oppstart - Skal vise deltakerliste data") {

                val doc =
                    renderVentelistebrev(
                        baseDto(
                            oppstartstype = Oppstartstype.LOPENDE,
                        ),
                    )
                doc.text() shouldContain "Du er satt på venteliste"
                doc.text() shouldNotContain "Ordinær oppstart er"
            }
        }
    }) {
    companion object {
        private fun renderVentelistebrev(payload: VentelistebrevPdfDto): Document = render("ventelistebrev", payload)

        private fun baseDeltaker() =
            VentelistebrevPdfDto.DeltakerDto(
                fornavn = "Ola",
                mellomnavn = null,
                etternavn = "Nordmann",
                personident = "12345678910",
                opprettetDato = LocalDate.now(),
            )

        private fun baseDeltakerliste(oppstartstype: Oppstartstype = Oppstartstype.FELLES) =
            VentelistebrevPdfDto.DeltakerlisteDto(
                ingressNavn = "ingressnavn",
                tittelNavn = "tittelnavn",
                arrangor = ArrangorDto("Arrangør AS"),
                startdato = LocalDate.now().plusDays(5),
                sluttdato = LocalDate.now().plusDays(10),
                oppmoteSted = "Oppmøtested",
                oppstartstype = oppstartstype,
            )

        private fun baseAvsender() =
            AvsenderDto(
                navn = "Nav Saksbehandler",
                enhet = "Nav Oslo",
            )

        private fun baseDto(
            oppstartstype: Oppstartstype = Oppstartstype.FELLES,
            deltaker: VentelistebrevPdfDto.DeltakerDto = baseDeltaker(),
        ) = VentelistebrevPdfDto(
            deltaker = deltaker,
            deltakerliste = baseDeltakerliste(oppstartstype = oppstartstype),
            avsender = baseAvsender(),
            opprettetDato = LocalDate.now(),
        )
    }
}
