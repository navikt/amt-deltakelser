package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navtiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
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
    override val innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
    override val navAnsattRepository: NavAnsattRepository = NavAnsattRepository()
    override val navBrukerRepository: NavBrukerRepository = NavBrukerRepository()
    override val navEnhetRepository: NavEnhetRepository = NavEnhetRepository()
    override val tiltakstypeRepository: TiltakstypeRepository = TiltakstypeRepository()
    override val vedtakRepository: VedtakRepository = VedtakRepository()
    override val vurderingRepository: VurderingRepository = VurderingRepository()

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }
}
