package no.nav.amt.deltaker.bff.innbygger

import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagVedtak
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDateTime

object InnbyggerTestUtils {
    fun deltakerMedIkkeFattetVedtak(): Deltaker {
        val deltaker = lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            historikk = false,
        )
        val vedtak = lagVedtak(deltakerVedVedtak = deltaker, fattet = null)

        return deltaker.copy(historikk = listOf(DeltakerHistorikk.Vedtak(vedtak)))
    }

    fun Deltaker.fattVedtak(): Deltaker {
        val vedtak = this.ikkeFattetVedtak.shouldNotBeNull()

        return this.copy(
            historikk = this.historikk
                .filter { it.id != vedtak.id }
                .plus(
                    DeltakerHistorikk.Vedtak(
                        vedtak.copy(
                            fattet = LocalDateTime.now(),
                            sistEndret = LocalDateTime.now(),
                        ),
                    ),
                ),
        )
    }

    private val DeltakerHistorikk.id
        get() = when (this) {
            is DeltakerHistorikk.Endring -> endring.id
            is DeltakerHistorikk.Vedtak -> vedtak.id
            is DeltakerHistorikk.Forslag -> forslag.id
            is DeltakerHistorikk.EndringFraArrangor -> endringFraArrangor.id
            is DeltakerHistorikk.ImportertFraArena -> importertFraArena.deltakerId
            is DeltakerHistorikk.VurderingFraArrangor -> data.id
            is DeltakerHistorikk.EndringFraTiltakskoordinator -> endringFraTiltakskoordinator.id
            is DeltakerHistorikk.InnsokPaaFellesOppstart -> data.id
        }
}
