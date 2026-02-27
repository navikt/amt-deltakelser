package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import org.junit.jupiter.api.Test

class EndreInnholdValideringTest {
    @Test
    fun `valider - innhold er uendret - feiler`() {
        shouldThrow<IllegalArgumentException> {
            val tiltaksinnhold = TestData.lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                deltakerliste = TestData.lagDeltakerliste(
                    tiltakstype = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold("Type", "type", true, null)),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(InnholdRequest("type", null)),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - lagt til innholdselement - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val tiltaksinnhold = TestData.lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    Innholdselement("Type2", "type2"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                deltakerliste = TestData.lagDeltakerliste(
                    tiltakstype = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold("Type", "type", true, null)),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(
                    InnholdRequest("type", null),
                    InnholdRequest("type2", null),
                ),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - endret tekst for annet-element - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val tiltaksinnhold = TestData.lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                deltakerliste = TestData.lagDeltakerliste(
                    tiltakstype = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold(annetInnholdselement.tekst, annetInnholdselement.innholdskode, true, "Gammel tekst")),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(
                    InnholdRequest(annetInnholdselement.innholdskode, "Ny tekst"),
                ),
            )

            request.valider(deltaker)
        }
    }
}
