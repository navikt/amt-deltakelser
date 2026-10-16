package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class TotrinnskontrollConsumerPrisendringTest : TotrinnskontrollConsumerTestBase() {
    private val navEnhet: NavEnhet = lagNavEnhet()
    private val navAnsatt: NavAnsatt = lagNavAnsatt(navIdent = "Z999999", navEnhetId = navEnhet.id)
    private val testGjennomforingId: UUID = UUID.randomUUID()
    private val deltakerInTest: Deltaker = lagDeltaker(
        deltakerliste = lagDeltakerliste(id = testGjennomforingId),
    )

    @BeforeEach
    fun setup() {
        every {
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = any(),
                prisinformasjonId = any(),
                godkjentAv = any(),
                godkjentAvEnhet = any(),
            )
        } returns true
        every {
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = any(),
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )
        } returns lagIngenKostnader()
        every {
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = any(),
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )
        } returns lagIngenKostnader()
        every { distribuerEndringService.produceHendelse(any(), any(), any(), any()) } just Runs
        every { deltakerEndringRepository.getForDeltaker(deltakerInTest.id) } returns listOf(
            lagPrisinfoEndring(deltakerInTest.id, totrinnskontrollId),
        )
    }

    @Nested
    inner class ProcessGodkjentPrisEndringTests {
        @Test
        fun `kaller godkjennOkonomi med gjennomforingId`() = runTest {
            // Arrange: default stubs and test data are configured in setup().

            // Act
            processGodkjentPrisEndring(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

            // Assert
            verify {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = testGjennomforingId,
                    prisinformasjonId = totrinnskontrollId,
                    godkjentAv = navAnsatt.id,
                    godkjentAvEnhet = navEnhet.id,
                )
            }
        }

        @Test
        fun `processGodkjentPrisEndring - kaster unntak når godkjennOkonomi feiler`() = runTest {
            // Arrange
            val exception = RuntimeException("Database error")
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } throws exception

            // Act
            val thrown = shouldThrow<RuntimeException> {
                processGodkjentPrisEndring(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)
            }

            // Assert
            thrown shouldBe exception
        }

        @Test
        fun `processGodkjentPrisEndring - publiserer ikke når godkjennOkonomi returnerer false`() = runTest {
            // Arrange
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns false

            // Act
            processGodkjentPrisEndring(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

            // Assert
            verify { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) }
            verify(exactly = 0) {
                PrisinfoRepoAdapter.hentPrisinfo(any<UUID>(), PrisinfoDbo.Rolle.GJELDENDE)
            }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class HentBegrunnelseTests {
        @Test
        fun `hentBegrunnelse - returnerer begrunnelse for prisinfoId`() {
            // Arrange
            val annenPrisinfoId = UUID.randomUUID()
            val forventetBegrunnelse = "Begrunnelse for godkjent prisendring"
            every { deltakerEndringRepository.getForDeltaker(deltakerInTest.id) } returns listOf(
                lagPrisinfoEndring(deltakerInTest.id, annenPrisinfoId, "Annen begrunnelse"),
                lagPrisinfoEndring(deltakerInTest.id, totrinnskontrollId, forventetBegrunnelse),
            )

            // Act
            val begrunnelse = consumer.hentBegrunnelse(deltakerInTest.id, totrinnskontrollId)

            // Assert
            begrunnelse shouldBe forventetBegrunnelse
        }

        @Test
        fun `hentBegrunnelse - kaster unntak når prisinfoendring ikke finnes`() {
            // Arrange
            every { deltakerEndringRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()

            // Act
            val exception = shouldThrow<IllegalStateException> {
                consumer.hentBegrunnelse(deltakerInTest.id, totrinnskontrollId)
            }

            // Assert
            exception.message shouldBe
                "Fant ikke prisinfoendring for deltaker ${deltakerInTest.id} og prisinfo $totrinnskontrollId"
        }

        @Test
        fun `hentBegrunnelse - kaster unntak når prisinfoendring mangler begrunnelse`() {
            // Arrange
            every { deltakerEndringRepository.getForDeltaker(deltakerInTest.id) } returns listOf(
                lagPrisinfoEndring(deltakerInTest.id, totrinnskontrollId, begrunnelse = null),
            )

            // Act
            val exception = shouldThrow<IllegalStateException> {
                consumer.hentBegrunnelse(deltakerInTest.id, totrinnskontrollId)
            }

            // Assert
            exception.message shouldBe
                "Prisinfoendring mangler begrunnelse for deltaker ${deltakerInTest.id} og prisinfo $totrinnskontrollId"
        }
    }

    private companion object {
        fun lagIngenKostnader() = PrisinformasjonDto.IngenKostnader(
            aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = null,
        )

        fun lagPrisinfoEndring(
            deltakerId: UUID,
            prisinfoId: UUID,
            begrunnelse: String? = "Prisendringens begrunnelse",
        ) = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            endring = DeltakerEndring.Endring.EndrePrisinfo(
                prisinfo = lagIngenKostnader(),
                begrunnelse = begrunnelse,
                prisinformasjonId = prisinfoId,
            ),
            endretAv = UUID.randomUUID(),
            endretAvEnhet = UUID.randomUUID(),
            endret = LocalDateTime.now(),
            forslag = null,
        )

        fun processGodkjentPrisEndring(
            consumer: TotrinnskontrollConsumer,
            deltaker: Deltaker,
            prisinfoId: UUID,
            navAnsatt: NavAnsatt,
            navEnhet: NavEnhet,
        ) {
            consumer.processGodkjentPrisEndring(
                deltaker = deltaker,
                prisinfoId = prisinfoId,
                behandletAvNavAnsatt = navAnsatt,
                behandletAvNavEnhet = navEnhet,
                godkjentAvNavAnsatt = navAnsatt,
                godkjentAvNavEnhet = navEnhet,
            )
        }
    }
}
