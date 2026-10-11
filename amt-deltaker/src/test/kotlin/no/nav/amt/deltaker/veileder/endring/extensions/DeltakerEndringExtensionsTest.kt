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
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
    fun `fjern oppstartsdato - oppdaterer deltakerens deltakelsesmengde fra gjenopprettet tidslinje`() {
        // Arrange
        val idag = LocalDate.now()
        val startdato = idag.plusDays(2)
        val deltakelsesmengder = Deltakelsesmengder(
            mengder = listOf(
                Deltakelsesmengde(100F, 5F, idag.minusDays(1), idag.minusDays(1).atStartOfDay()),
                Deltakelsesmengde(50F, 3F, startdato, startdato.atStartOfDay()),
            ),
            startdato = startdato,
        )
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = 50F,
            dagerPerUke = 3F,
        )

        // Act
        val resultat = DeltakerEndring.Endring.FjernOppstartsdato(begrunnelse = null)
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = { deltakelsesmengder },
            ).shouldBeSuccess()

        // Assert
        assertSoftly(resultat.deltaker) {
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
            deltakelsesprosent shouldBe 100F
            dagerPerUke shouldBe 5F
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
