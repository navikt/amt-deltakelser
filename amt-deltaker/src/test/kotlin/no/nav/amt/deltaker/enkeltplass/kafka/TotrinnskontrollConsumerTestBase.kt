package no.nav.amt.deltaker.enkeltplass.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

abstract class TotrinnskontrollConsumerTestBase {
    protected val deltakerRepository = mockk<DeltakerRepository>()
    protected val deltakerService = mockk<DeltakerService>()
    protected val vedtakService = mockk<VedtakService>()
    protected val distribuerEndringService = mockk<DistribuerEndringService>()
    protected val navAnsattService = mockk<NavAnsattService>(relaxed = true)

    protected val consumer = TotrinnskontrollConsumer(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        vedtakService = vedtakService,
        distribuerEndringService = distribuerEndringService,
        navAnsattService = navAnsattService,
    )

    protected val gjennomforingId: UUID = UUID.randomUUID()
    protected val totrinnskontrollId: UUID = UUID.randomUUID()
    protected val navEnhetInTest = lagNavEnhet()
    protected val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

    @BeforeEach
    fun setupConsumerMocks() {
        mockkObject(Database)
        every { Database.transaction<Any>(any()) } answers {
            firstArg<() -> Any>().invoke()
        }
        mockkObject(PrisinfoRepoAdapter)
        mockkObject(PrisinfoRepository)
    }

    @AfterEach
    fun tearDownConsumerMocks() {
        unmockkObject(Database)
        unmockkObject(PrisinfoRepoAdapter)
        unmockkObject(PrisinfoRepository)
    }
}
