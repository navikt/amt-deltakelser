package no.nav.amt.deltaker.api

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import org.junit.jupiter.api.Test

class InputvalideringTest {
    @Test
    fun testValiderKladdInnhold() {
        val tiltaksinnhold = lagDeltakerRegistreringInnhold(
            innholdselementer = listOf(
                Innholdselement("Type", "type"),
                annetInnholdselement,
            ),
        )
        val tiltakstype = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING

        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdsElementRequest("type", null)), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdsElementRequest("type", null)),
                lagDeltakerRegistreringInnhold(innholdselementer = emptyList()),
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(emptyList(), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(emptyList(), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdsElementRequest("foo", null)), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, null)), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "")), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "annet innhold må ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdsElementRequest("type", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdsElementRequest("type", "andre typer enn annet skal ikke ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
    }
}
