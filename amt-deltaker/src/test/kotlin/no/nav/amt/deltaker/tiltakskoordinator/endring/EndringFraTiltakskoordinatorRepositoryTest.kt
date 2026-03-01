package no.nav.amt.deltaker.tiltakskoordinator.endring

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class EndringFraTiltakskoordinatorRepositoryTest {
    private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `insert - ingen endring - feiler ikke`() {
        endringFraTiltakskoordinatorRepository.insert(emptyList()) shouldBe Unit
    }

    @Test
    fun `insert - en endring - inserter`() {
        with(EndringFraTiltakskoordinatorCtx()) {
            endringFraTiltakskoordinatorRepository.insert(listOf(endring))
            assertEndringLagret(this)
        }
    }

    @Test
    fun `insert - flere endringer - inserter`() {
        val endring1Ctx = EndringFraTiltakskoordinatorCtx()
        val endring2Ctx = EndringFraTiltakskoordinatorCtx()

        endringFraTiltakskoordinatorRepository.insert(listOf(endring1Ctx.endring, endring2Ctx.endring))

        assertEndringLagret(endring1Ctx)
        assertEndringLagret(endring2Ctx)
    }

    private fun assertEndringLagret(ctx: EndringFraTiltakskoordinatorCtx) {
        val lagretEndring1 = endringFraTiltakskoordinatorRepository.getForDeltaker(ctx.deltaker.id).first()
        sammenlignEndringFraTiltakskoordinator(ctx.endring, lagretEndring1)
    }
}

fun sammenlignEndringFraTiltakskoordinator(a: EndringFraTiltakskoordinator, b: EndringFraTiltakskoordinator) {
    a.id shouldBe b.id
    a.deltakerId shouldBe b.deltakerId
    a.endring shouldBe b.endring
    a.endretAv shouldBe b.endretAv
    a.endret shouldBeCloseTo b.endret
}

data class EndringFraTiltakskoordinatorCtx(
    val navAnsatt: NavAnsatt = TestData.lagNavAnsatt(),
    val navEnhet: NavEnhet = TestData.lagNavEnhet(navAnsatt.navEnhetId!!),
    val deltakerliste: Deltakerliste = TestData.lagDeltakerlisteMedTrengerGodkjenning(),
    val navBruker: NavBruker = lagNavBruker(),
    var deltaker: Deltaker = TestData.lagDeltaker(
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
    ),
    val endring: EndringFraTiltakskoordinator = EndringFraTiltakskoordinator(
        id = UUID.randomUUID(),
        deltakerId = deltaker.id,
        endring = EndringFraTiltakskoordinator.DelMedArrangor,
        endretAv = navAnsatt.id,
        endretAvEnhet = navEnhet.id,
        endret = LocalDateTime.now(),
    ),
) {
    private val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()

    init {
        TestRepository.insertAll(navEnhet, navAnsatt, deltakerliste, deltaker)
    }

    fun medStatusDeltar() {
        deltaker = deltaker.copy(
            id = UUID.randomUUID(),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)
    }

    fun medInnsok() {
        val innsok = TestData.lagInnsoktPaaKurs(
            deltakerId = deltaker.id,
            innsoktAv = navAnsatt.id,
            innsoktAvEnhet = navEnhet.id,
        )

        innsokPaaFellesOppstartRepository.insert(innsok)
    }
}
