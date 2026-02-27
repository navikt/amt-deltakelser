package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerExtensionsTest {
    @Test
    fun `endreDeltakersOppstart - fremtidig startdato - endrer også deltakelsesmengde`() {
        val nyStartdato = LocalDate.now().plusMonths(1)
        val gammelStartdato = nyStartdato.minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = nyStartdato,
            opprettet = nyStartdato.atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = deltaker.endreDeltakersOppstart(
            startdato = nyStartdato,
            sluttdato = null,
            deltakelsesmengder = gammelDeltakelsesmengder,
        )

        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe fremtidigMengde.dagerPerUke
            deltakelsesprosent shouldBe fremtidigMengde.deltakelsesprosent
        }
    }

    @Test
    fun `endreDeltakersOppstart - null startdato - endrer ikke deltakelsesmengde`() {
        val nyStartdato = null
        val gammelStartdato = LocalDate.now().minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = gammelStartdato.plusMonths(2),
            opprettet = gammelStartdato.plusMonths(2).atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = deltaker.endreDeltakersOppstart(
            startdato = nyStartdato,
            sluttdato = null,
            deltakelsesmengder = gammelDeltakelsesmengder,
        )

        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
            deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
        }
    }

    @Test
    fun `endreDeltakersOppstart - eldre startdato - endrer ikke deltakelsesmengde`() {
        val gammelStartdato = LocalDate.now().minusMonths(1)
        val nyStartdato = gammelStartdato.minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = gammelStartdato.plusMonths(2),
            opprettet = gammelStartdato.plusMonths(2).atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = deltaker.endreDeltakersOppstart(
            startdato = nyStartdato,
            sluttdato = null,
            deltakelsesmengder = gammelDeltakelsesmengder,
        )

        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
            deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
        }
    }
}
