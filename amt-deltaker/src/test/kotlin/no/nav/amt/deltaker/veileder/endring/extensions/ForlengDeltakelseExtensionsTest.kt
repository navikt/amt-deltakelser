package no.nav.amt.deltaker.veileder.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ForlengDeltakelseExtensionsTest {
    @Test
    fun `oppdaterDeltaker - forleng deltakelse frem i tid - endrer sluttdato`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusWeeks(1),
        )
        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.DELTAR
        }
    }

    @Test
    fun `oppdaterDeltaker - forleng deltakelse, har sluttet - status endres til deltar`() {
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.now().minusMonths(2),
            sluttdato = LocalDate.now().minusDays(1),
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )
        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.DELTAR
        }
    }

    @Test
    fun `oppdaterDeltaker - forleng med samme sluttdato - returnerer failure`() {
        val sluttdato = LocalDate.now().plusWeeks(4)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = sluttdato,
        )
        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = sluttdato, // Samme som nåværende
            begrunnelse = null,
        )

        endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeFailure()
    }

    @Test
    fun `forlengDeltakelse - fremtidig mengde innenfor ny sluttdato inkluderes i periode`() {
        // Docs-scenario 05.01.2025: sluttdato forlenges slik at en fremtidig mengde blir gyldig igjen
        val startdato = LocalDate.now().minusMonths(1)
        val fremtidigMengdeGyldigFra = LocalDate.now().plusDays(10)
        val gammelSluttdato = LocalDate.now().plusDays(5) // Kortere enn fremtidigMengde
        val nySluttdato = LocalDate.now().plusMonths(3) // Lengre enn fremtidigMengde

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 40F,
            dagerPerUke = 2F,
            gyldigFra = startdato,
            opprettet = startdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = fremtidigMengdeGyldigFra,
            opprettet = fremtidigMengdeGyldigFra.atStartOfDay(),
        )
        val deltakelsesmengder = Deltakelsesmengder(listOf(gjeldendeMengde, fremtidigMengde))

        // Før forlengelse: fremtidig mengde ekskluderes fordi gyldigFra > gammelSluttdato
        val perioedFoerForlengelse = deltakelsesmengder.periode(startdato, gammelSluttdato)
        perioedFoerForlengelse.size shouldBe 1

        // Etter forlengelse: fremtidig mengde inkluderes fordi gyldigFra < nySluttdato
        val periodeEtterForlengelse = deltakelsesmengder.periode(startdato, nySluttdato)
        periodeEtterForlengelse.size shouldBe 2
        periodeEtterForlengelse.last().gyldigFra shouldBe fremtidigMengdeGyldigFra
    }
}
