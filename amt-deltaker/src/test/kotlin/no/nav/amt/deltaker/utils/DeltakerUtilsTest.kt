package no.nav.amt.deltaker.utils

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.navtiltakskoordinator.endring.EndringFraTiltakskoordinatorCtx
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerUtilsTest {
    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }

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

    @Test
    fun `sjekkEndringUtfall - tildel plass - felles oppstart med fremtidig startdato setter datoer`() = runTest {
        with(
            EndringFraTiltakskoordinatorCtx(
                deltakerliste = lagDeltakerliste(
                    oppstart = Oppstartstype.FELLES,
                    startDato = LocalDate.now().plusDays(2),
                    sluttDato = LocalDate.now().plusDays(30),
                ),
            ),
        ) {
            val endretDeltaker = DeltakerUtils
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.TildelPlass,
                ).getOrThrow()

            endretDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            endretDeltaker.startdato shouldBe deltaker.deltakerliste.startDato
            endretDeltaker.sluttdato shouldBe deltaker.deltakerliste.sluttDato
        }
    }

    @Test
    fun `sjekkEndringUtfall - tildel plass - felles oppstart med passert startdato setter ikke datoer`() = runTest {
        with(
            EndringFraTiltakskoordinatorCtx(
                deltakerliste = lagDeltakerliste(
                    oppstart = Oppstartstype.FELLES,
                    startDato = LocalDate.now().minusDays(1),
                    sluttDato = LocalDate.now().plusDays(30),
                ),
            ),
        ) {
            val endretDeltaker = DeltakerUtils
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.TildelPlass,
                ).getOrThrow()

            endretDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            endretDeltaker.startdato shouldBe null
            endretDeltaker.sluttdato shouldBe null
        }
    }

    @Test
    fun `sjekkEndringUtfall - tildel plass - lopende oppstart setter ikke datoer`() = runTest {
        with(
            EndringFraTiltakskoordinatorCtx(
                deltakerliste = lagDeltakerliste(
                    oppstart = Oppstartstype.LOPENDE,
                    startDato = LocalDate.now().plusDays(2),
                    sluttDato = LocalDate.now().plusDays(30),
                ),
            ),
        ) {
            val endretDeltaker = DeltakerUtils
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.TildelPlass,
                ).getOrThrow()

            endretDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            endretDeltaker.startdato shouldBe null
            endretDeltaker.sluttdato shouldBe null
        }
    }
}
