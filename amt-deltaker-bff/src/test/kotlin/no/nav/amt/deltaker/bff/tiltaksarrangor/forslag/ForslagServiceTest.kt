package no.nav.amt.deltaker.bff.tiltaksarrangor.forslag

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.deltaker.bff.utils.assertProduced
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.outbox.OutboxRecord
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class ForslagServiceTest {
    private val navEnhetService = mockk<NavEnhetService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val outboxService = mockk<OutboxService>()
    private val arrangorMeldingProducer = ArrangorMeldingProducer(outboxService)

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

    @BeforeEach
    fun setup() {
        every {
            outboxService.insertRecord(any(), any(), any(), any())
        } returns mockk<OutboxRecord>()
    }

    @Test
    fun `avvisForslag - produserer avvist forslag og sletter i db`() = runTest {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)
        val navAnsatt = lagNavAnsatt()
        val navEnhet = lagNavEnhet()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent) } returns navAnsatt
        coEvery { navEnhetService.hentOpprettEllerOppdaterNavEnhet(navEnhet.enhetsnummer) } returns navEnhet
        val opprinneligForslag = TestData.lagForslag(deltakerId = deltaker.id)
        forslagRepository.upsert(opprinneligForslag)
        val begrunnelseAvslag = "Avslått fordi.."

        forslagService.avvisForslag(opprinneligForslag, begrunnelseAvslag, navAnsatt.navIdent, navEnhet.enhetsnummer)

        forslagRepository.get(opprinneligForslag.id).getOrNull() shouldBe null

        outboxService.assertProduced(
            opprinneligForslag.copy(
                status = Forslag.Status.Avvist(Forslag.NavAnsatt(navAnsatt.id, navEnhet.id), LocalDateTime.now(), begrunnelseAvslag),
            ),
        )
    }
}
