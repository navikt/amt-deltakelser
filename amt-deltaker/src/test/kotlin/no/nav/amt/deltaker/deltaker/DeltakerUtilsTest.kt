package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorCtx
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.jupiter.api.Test

class DeltakerUtilsTest {
    @Test
    fun `sjekkEndringUtfall - del med arrangør - oppdaterer attributt`() = runTest {
        with(EndringFraTiltakskoordinatorCtx()) {
            val endretDeltaker = DeltakerUtils
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                ).getOrThrow()

            endretDeltaker.erManueltDeltMedArrangor shouldBe true
            endretDeltaker.status.type shouldBe deltaker.status.type
        }
    }

    @Test
    fun `sjekkEndringUtfall - del med arrangør - ugyldig endring - returnerer failure`() = runTest {
        with(EndringFraTiltakskoordinatorCtx()) {
            medStatusDeltar()
            val resultat = DeltakerUtils.sjekkEndringUtfall(
                deltaker,
                EndringFraTiltakskoordinator.DelMedArrangor,
            )

            resultat.isFailure shouldBe true
        }
    }

    @Test
    fun `sjekkEndringUtfall - mangler oppfolgingsperiode - returnerer failure`() = runTest {
        with(
            EndringFraTiltakskoordinatorCtx(
                navBruker = TestData.lagNavBruker().copy(oppfolgingsperioder = emptyList()),
            ),
        ) {
            val resultat = DeltakerUtils.sjekkEndringUtfall(
                deltaker,
                EndringFraTiltakskoordinator.DelMedArrangor,
            )

            resultat.isFailure shouldBe true
        }
    }
}
