package no.nav.amt.deltaker.bff.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.bff.utils.TestData
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeltakerTest {
    @Test
    fun `fattetVedtak - flere vedtak - henter vedtaket som er gyldig og fattet`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val fattet = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            gyldigTil = LocalDateTime.now().minusMonths(1),
        )
        val fattet2 = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDateTime.now().minusMonths(1),
            gyldigTil = null,
        )

        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(fattet, fattet2))

        sammenlignVedtak(deltakerMedVedtak.fattetVedtak!!, fattet2)
    }

    @Test
    fun `fattetVedtak - ingen fattet vedtak - returnere null`() {
        val deltaker = TestData.lagDeltaker(historikk = false)

        deltaker.fattetVedtak shouldBe null
    }

    @Test
    fun `getIkkeFattetVedtak - deltaker har ikke fattet vedtak - returnerer vedtak`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = null,
        )
        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(vedtak))
        sammenlignVedtak(deltakerMedVedtak.ikkeFattetVedtak!!, vedtak)
    }

    @Test
    fun `getIkkeFattetVedtak - deltaker har kun fattet vedtak - returnerer null`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val fattet = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDateTime.now().minusMonths(2),
        )
        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(fattet))
        deltakerMedVedtak.ikkeFattetVedtak shouldBe null
    }
}
