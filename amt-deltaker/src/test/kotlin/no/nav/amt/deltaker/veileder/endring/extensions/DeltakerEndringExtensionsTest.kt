package no.nav.amt.deltaker.veileder.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.toEndring
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
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
            .anvendPaaDeltaker(
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
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.SOKT_INN
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }

    @Test
    fun `oppdaterDeltaker - ikke aktuell fra har sluttet - returnerer ikke aktuell`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
            ),
        )

        val resultat = ikkeAktuellRequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }

    @Test
    fun `oppdaterDeltaker - ikke aktuell naar allerede ikke aktuell med samme aarsak - returnerer feil`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
            ),
        )

        ikkeAktuellRequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeFailure()
    }

    companion object {
        private val reaktiverDeltakelseRequest = ReaktiverDeltakelseRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            begrunnelse = "begrunnelse",
        )

        private val ikkeAktuellRequest = IkkeAktuellRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
        )
    }
}
