package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.extension.RegisterExtension

abstract class IntegrationTestWithDbBase : IntegrationTestBase() {
    override val arrangorRepository: ArrangorRepository = ArrangorRepository()
    override val deltakerEndringRepository: DeltakerEndringRepository = DeltakerEndringRepository()
    override val deltakerRepository: DeltakerRepository = DeltakerRepository()
    override val deltakerlisteRepository: DeltakerlisteRepository = DeltakerlisteRepository()
    override val endringFraArrangorRepository: EndringFraArrangorRepository = EndringFraArrangorRepository()
    override val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()
    override val forslagRepository: ForslagRepository = ForslagRepository()
    override val importertFraArenaRepository: ImportertFraArenaRepository = ImportertFraArenaRepository()
    override val innsokRepository: InnsokRepository = InnsokRepository()
    override val navAnsattRepository: NavAnsattRepository = NavAnsattRepository()
    override val navBrukerRepository: NavBrukerRepository = NavBrukerRepository()
    override val navEnhetRepository: NavEnhetRepository = NavEnhetRepository()
    override val tiltakRepository: TiltakRepository = TiltakRepository()
    override val vedtakRepository: VedtakRepository = VedtakRepository()
    override val vurderingRepository: VurderingRepository = VurderingRepository()

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }
}
