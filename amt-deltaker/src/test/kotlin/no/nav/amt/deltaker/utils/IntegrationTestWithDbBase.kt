package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.extension.RegisterExtension

abstract class IntegrationTestWithDbBase : IntegrationTestBase() {
    override val navEnhetRepository: NavEnhetRepository = NavEnhetRepository()
    override val navAnsattRepository: NavAnsattRepository = NavAnsattRepository()
    override val navBrukerRepository: NavBrukerRepository = NavBrukerRepository()
    override val deltakerlisteRepository: DeltakerlisteRepository = DeltakerlisteRepository()
    override val deltakerRepository: DeltakerRepository = DeltakerRepository()
    override val tiltakstypeRepository: TiltakstypeRepository = TiltakstypeRepository()
    override val arrangorRepository: ArrangorRepository = ArrangorRepository()

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }
}
