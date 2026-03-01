package no.nav.amt.deltaker.bff.deltaker.forslag

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.bff.kafka.utils.assertProduced
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.TestOutboxEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class ForslagServiceTest {
    private val navEnhetService = mockk<NavEnhetService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val arrangorMeldingProducer = ArrangorMeldingProducer(TestOutboxEnvironment.outboxService)

    private val forslagRepository = ForslagRepository()
    private val forslagService = ForslagService(
        forslagRepository = forslagRepository,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        arrangorMeldingProducer = arrangorMeldingProducer,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `avvisForslag - produserer avvist forslag og sletter i db`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent) } returns navAnsatt
        coEvery { navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhet.enhetsnummer) } returns navEnhet
        val opprinneligForslag = TestData.lagForslag(deltakerId = deltaker.id)
        forslagRepository.upsert(opprinneligForslag)
        val begrunnelseAvslag = "Avslått fordi.."

        forslagService.avvisForslag(opprinneligForslag, begrunnelseAvslag, navAnsatt.navIdent, navEnhet.enhetsnummer)

        forslagRepository.get(opprinneligForslag.id).getOrNull() shouldBe null

        assertProduced(
            opprinneligForslag.copy(
                status = Forslag.Status.Avvist(Forslag.NavAnsatt(navAnsatt.id, navEnhet.id), LocalDateTime.now(), begrunnelseAvslag),
            ),
        )
    }
}
