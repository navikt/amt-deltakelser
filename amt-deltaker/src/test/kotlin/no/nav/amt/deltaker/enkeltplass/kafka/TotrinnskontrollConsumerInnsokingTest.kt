package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.service.DeltakerService
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
import java.time.LocalDateTime
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
        val deltakerInTest = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))

        // Act
        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Assert
        verify(exactly = 0) {
            deltakerService.upsertAndProduceDeltaker(
                deltaker = any(),
                erDeltakerSluttdatoEndret = any(),
                forceProduce = any(),
                nesteStatus = any(),
                beforeUpsert = any(),
                afterUpsert = any(),
            )
        }
        verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) }
    }

    @Test
    fun `processGodkjentInnsoking - oppdaterer deltaker status via beforeUpsert`() = runTest {
        // Arrange
        val deltakerInTest = lagSoktInnDeltaker()
        val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
        stubBeforeUpsert(
            deltakerService,
            slot = beforeUpsertSlot,
            result = deltakerInTest.copy(
                vedtaksinformasjon = Vedtaksinformasjon(
                    fattet = null,
                    fattetAvNav = false,
                    opprettet = LocalDateTime.now(),
                    opprettetAv = UUID.randomUUID(),
                    opprettetAvEnhet = UUID.randomUUID(),
                    sistEndret = LocalDateTime.now(),
                    sistEndretAv = UUID.randomUUID(),
                    sistEndretAvEnhet = UUID.randomUUID(),
                ),
            ),
        )

        // Act
        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Assert
        val updated = beforeUpsertSlot.captured(deltakerInTest)
        updated.status.type shouldBe DeltakerStatus.Type.DELTAR
        verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest, any(), any()) }
    }

    @Test
    fun `processGodkjentInnsoking - skipper videre prosessering naar prisinfo ikke kan godkjennes`() = runTest {
        // Arrange
        val deltakerInTest = lagSoktInnDeltaker(startdato = null, sluttdato = null)
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns false
        val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
        stubUpsertApplyingBeforeCallback(deltakerService, beforeUpsertSlot)

        // Act
        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Assert
        verify(exactly = 1) {
            deltakerService.upsertAndProduceDeltaker(
                deltaker = any(),
                erDeltakerSluttdatoEndret = any(),
                forceProduce = any(),
                nesteStatus = any(),
                beforeUpsert = any(),
                afterUpsert = any(),
            )
        }
        verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) }
        verify(exactly = 0) { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) }
    }

    @Test
    fun `processGodkjentInnsoking - beforeUpsert kaster exception ved historisk prisinfo`() = runTest {
        // Arrange
        val deltakerInTest = lagSoktInnDeltaker(startdato = null, sluttdato = null)
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns false
        val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
        stubBeforeUpsert(deltakerService, beforeUpsertSlot, deltakerInTest)

        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Act
        val exception = shouldThrow<IllegalStateException> {
            beforeUpsertSlot.captured(deltakerInTest)
        }

        // Assert
        exception.message shouldBe
            "Fant ikke aktiv ENDRING-prisinfo $totrinnskontrollId for deltaker ${deltakerInTest.id}"
    }

    @Test
    fun `processGodkjentInnsoking - afterUpsert produserer hendelse`() = runTest {
        // Arrange
        val sistEndretAv = UUID.randomUUID()
        val sistEndretAvEnhet = UUID.randomUUID()
        val vedtaksinformasjon = Vedtaksinformasjon(
            fattet = null,
            fattetAvNav = false,
            opprettet = LocalDateTime.now(),
            opprettetAv = UUID.randomUUID(),
            opprettetAvEnhet = UUID.randomUUID(),
            sistEndret = LocalDateTime.now(),
            sistEndretAv = sistEndretAv,
            sistEndretAvEnhet = sistEndretAvEnhet,
        )
        val deltakerInTest = lagSoktInnDeltaker(vedtaksinformasjon = vedtaksinformasjon)
        val afterUpsertSlot = slot<(Deltaker) -> Unit>()
        stubAfterUpsert(deltakerService, afterUpsertSlot, deltakerInTest)

        // Act
        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Assert
        afterUpsertSlot.captured(deltakerInTest)
        verify {
            distribuerEndringService.produceHendelseForUtkast(
                deltaker = deltakerInTest,
                navAnsatt = any(),
                enhet = any(),
                block = any(),
            )
        }
    }

    @Test
    fun `processGodkjentInnsoking - afterUpsert produserer hendelse når vedtaksinformasjon mangler`() = runTest {
        // Arrange
        val deltakerInTest = lagSoktInnDeltaker(vedtaksinformasjon = null)
        val afterUpsertSlot = slot<(Deltaker) -> Unit>()
        stubAfterUpsert(deltakerService, afterUpsertSlot, deltakerInTest)

        // Act
        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Assert
        afterUpsertSlot.captured(deltakerInTest)
        verify {
            distribuerEndringService.produceHendelseForUtkast(
                deltaker = deltakerInTest,
                navAnsatt = any(),
                enhet = any(),
                block = any(),
            )
        }
    }

    @Test
    fun `processGodkjentInnsoking - kaster unntak når godkjennOkonomi feiler`() = runTest {
        // Arrange
        val deltakerInTest = lagSoktInnDeltaker()
        val dbError = RuntimeException("Database error")
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } throws dbError
        val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
        stubBeforeUpsert(deltakerService, beforeUpsertSlot, deltakerInTest)

        processGodkjentInnsoking(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

        // Act
        val exception = shouldThrow<RuntimeException> {
            beforeUpsertSlot.captured(deltakerInTest)
        }

        // Assert
        exception shouldBe dbError
    }

    private companion object {
        fun lagSoktInnDeltaker(
            startdato: LocalDate? = LocalDate.now(),
            sluttdato: LocalDate? = LocalDate.now().plusWeeks(4),
            vedtaksinformasjon: Vedtaksinformasjon? = null,
        ) = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            startdato = startdato,
            sluttdato = sluttdato,
            vedtaksinformasjon = vedtaksinformasjon,
        )

        fun processGodkjentInnsoking(
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

        fun stubBeforeUpsert(
            deltakerService: DeltakerService,
            slot: CapturingSlot<(Deltaker) -> Deltaker>,
            result: Deltaker,
        ) {
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = capture(slot),
                    afterUpsert = any(),
                )
            } returns result
        }

        fun stubAfterUpsert(
            deltakerService: DeltakerService,
            slot: CapturingSlot<(Deltaker) -> Unit>,
            result: Deltaker,
        ) {
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = any(),
                    afterUpsert = capture(slot),
                )
            } returns result
        }

        fun stubUpsertApplyingBeforeCallback(
            deltakerService: DeltakerService,
            slot: CapturingSlot<(Deltaker) -> Deltaker>,
        ) {
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = capture(slot),
                    afterUpsert = any(),
                )
            } answers {
                slot.captured(firstArg<Deltaker>())
            }
        }
    }
}
