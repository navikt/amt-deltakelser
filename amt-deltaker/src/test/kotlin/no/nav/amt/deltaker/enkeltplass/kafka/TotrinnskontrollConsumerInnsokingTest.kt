package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class TotrinnskontrollConsumerInnsokingTest : TotrinnskontrollConsumerTestBase() {
    private val navEnhet = lagNavEnhet()
    private val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)

    @BeforeEach
    fun setup() {
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns true
        every { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) } just Runs
        every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs
    }

    @Test
    fun `processGodkjentInnsoking - gjør ingenting når deltaker ikke har status SOKT_INN`() = runTest {
        // Arrange
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))

        // Act
        processGodkjentInnsoking(
            consumer = consumer,
            deltaker = deltaker,
            prisinfoId = totrinnskontrollId,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
        )

        // Assert
        verify(exactly = 0) {
            deltakerService.upsertAndProduceDeltakerConditionally(
                deltaker = any(),
                beforeUpsert = any(),
                afterUpsert = any(),
            )
        }
        verify(exactly = 0) {
            vedtakService.godkjentOkonomiFattVedtak(
                deltaker = any(),
                sistEndretAv = any(),
                sistEndretAvEnhet = any(),
            )
        }
    }

    @Test
    fun `processGodkjentInnsoking - oppdaterer status og publiserer hendelse for aktiv prisinfo`() = runTest {
        // Arrange
        val deltaker = lagSoktInnDeltaker()
        var deltakerForUpsert: Deltaker? = null
        stubConditionalUpsert { deltakerForUpsert = it }

        // Act
        processGodkjentInnsoking(
            consumer = consumer,
            deltaker = deltaker,
            prisinfoId = totrinnskontrollId,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
        )

        // Assert
        deltakerForUpsert.shouldNotBeNull().status.type shouldBe DeltakerStatus.Type.DELTAR
        verify { vedtakService.godkjentOkonomiFattVedtak(deltaker, any(), any()) }
        verify {
            distribuerEndringService.produceHendelseForUtkast(
                deltaker = match { it.status.type == DeltakerStatus.Type.DELTAR },
                navAnsatt = any(),
                enhet = any(),
                block = any(),
            )
        }
    }

    @Test
    fun `processGodkjentInnsoking - skipper videre prosessering for historisk prisinfo`() = runTest {
        // Arrange
        val deltaker = lagSoktInnDeltaker(startdato = null, sluttdato = null)
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns false
        var deltakerForUpsert: Deltaker? = null
        stubConditionalUpsert { deltakerForUpsert = it }

        // Act
        processGodkjentInnsoking(
            consumer = consumer,
            deltaker = deltaker,
            prisinfoId = totrinnskontrollId,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
        )

        // Assert
        deltakerForUpsert shouldBe null
        verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) }
        verify(exactly = 0) { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) }
    }

    @Test
    fun `processGodkjentInnsoking - lar feil fra prisinfo-godkjenning propagere`() = runTest {
        // Arrange
        val deltaker = lagSoktInnDeltaker()
        val dbError = RuntimeException("Database error")
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } throws dbError
        stubConditionalUpsert()

        // Act
        val exception = shouldThrow<RuntimeException> {
            processGodkjentInnsoking(
                consumer = consumer,
                deltaker = deltaker,
                prisinfoId = totrinnskontrollId,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
            )
        }

        // Assert
        exception shouldBe dbError
    }

    private fun stubConditionalUpsert(captureDeltaker: (Deltaker?) -> Unit = {}) {
        every {
            deltakerService.upsertAndProduceDeltakerConditionally(
                deltaker = any(),
                beforeUpsert = any(),
                afterUpsert = any(),
            )
        } answers {
            val deltaker = firstArg<Deltaker>()
            val oppdatertDeltaker = secondArg<(Deltaker) -> Deltaker?>()(deltaker)
            captureDeltaker(oppdatertDeltaker)
            oppdatertDeltaker?.also {
                thirdArg<(Deltaker) -> Unit>()(it)
            }
        }
    }

    private fun lagSoktInnDeltaker(
        startdato: LocalDate? = LocalDate.now(),
        sluttdato: LocalDate? = LocalDate.now().plusWeeks(4),
    ) = lagDeltaker(
        status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        startdato = startdato,
        sluttdato = sluttdato,
    )

    private fun processGodkjentInnsoking(
        consumer: TotrinnskontrollConsumer,
        deltaker: Deltaker,
        prisinfoId: UUID,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
    ) {
        consumer.processGodkjentInnsoking(
            deltaker = deltaker,
            prisinfoId = prisinfoId,
            behandletAvNavAnsatt = navAnsatt,
            behandletAvNavEnhet = navEnhet,
            godkjentAvNavAnsatt = navAnsatt,
            godkjentAvNavEnhet = navEnhet,
        )
    }
}
