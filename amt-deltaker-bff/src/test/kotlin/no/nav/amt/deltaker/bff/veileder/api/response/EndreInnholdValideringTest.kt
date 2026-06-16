package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.veileder.api.request.EndreInnholdRequest
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import org.junit.jupiter.api.Test

class EndreInnholdValideringTest {
    @Test
    fun `valider - innhold er uendret - feiler`() {
        shouldThrow<IllegalArgumentException> {
            val tiltaksinnhold = lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                gjennomforing = TestData.lagGjennomforingModel(
                    tiltak = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold("Type", "type", true, null)),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(InnholdsElementRequest("type", null)),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - lagt til innholdselement - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val tiltaksinnhold = lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    Innholdselement("Type2", "type2"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                gjennomforing = TestData.lagGjennomforingModel(
                    tiltak = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold("Type", "type", true, null)),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(
                    InnholdsElementRequest("type", null),
                    InnholdsElementRequest("type2", null),
                ),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - endret tekst for annet-element - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val tiltaksinnhold = lagDeltakerRegistreringInnhold(
                innholdselementer = listOf(
                    Innholdselement("Type", "type"),
                    annetInnholdselement,
                ),
            )
            val deltaker = TestData.lagDeltaker(
                gjennomforing = TestData.lagGjennomforingModel(
                    tiltak = TestData.lagTiltakstype(
                        innhold = tiltaksinnhold,
                    ),
                ),
                innhold = listOf(Innhold(annetInnholdselement.tekst, annetInnholdselement.innholdskode, true, "Gammel tekst")),
            )
            val request = EndreInnholdRequest(
                innhold = listOf(
                    InnholdsElementRequest(annetInnholdselement.innholdskode, "Ny tekst"),
                ),
            )

            request.valider(deltaker)
        }
    }
}
