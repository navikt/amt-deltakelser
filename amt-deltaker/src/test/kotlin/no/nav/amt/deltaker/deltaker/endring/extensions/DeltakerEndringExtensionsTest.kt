package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.randomEnhetsnummer
import no.nav.amt.deltaker.utils.data.TestData.randomNavIdent
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import org.junit.jupiter.api.Test

class DeltakerEndringExtensionsTest {
    @Test
    fun `oppdaterDeltaker - reaktiver deltakelse lopende oppstart`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedDirekteVedtak(),
        )

        val resultat = reaktiverDeltakelseRequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }

    @Test
    fun `oppdaterDeltaker - reaktiver deltakelse felles oppstart`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedTrengerGodkjenning(),
        )

        val resultat = reaktiverDeltakelseRequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.SOKT_INN
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }

    companion object {
        private val reaktiverDeltakelseRequest = ReaktiverDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            begrunnelse = "begrunnelse",
        )
    }
}
