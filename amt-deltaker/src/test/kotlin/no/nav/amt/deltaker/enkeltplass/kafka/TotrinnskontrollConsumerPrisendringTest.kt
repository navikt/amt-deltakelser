package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TotrinnskontrollConsumerPrisendringTest : TotrinnskontrollConsumerTestBase() {
    private val navEnhet: NavEnhet = lagNavEnhet()
    private val navAnsatt: NavAnsatt = lagNavAnsatt(navIdent = "Z999999", navEnhetId = navEnhet.id)
    private val testGjennomforingId: UUID = UUID.randomUUID()
    private val deltakerInTest: Deltaker = lagDeltaker(
        gjennomforing = lagDeltakerliste(id = testGjennomforingId),
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
        } returns lagIngenKostnader("Prisendringens begrunnelse")
        every { distribuerEndringService.produceHendelse(any(), any(), any(), any()) } just Runs
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
    inner class BegrunnelseTests {
        @Test
        fun `inkluderer begrunnelse fra prisinfo i hendelsen`() = runTest {
            // Arrange
            val forventetBegrunnelse = "Begrunnelse for godkjent prisendring"
            every {
                PrisinfoRepoAdapter.hentPrisinfo(testGjennomforingId, PrisinfoDbo.Rolle.GJELDENDE)
            } returns lagIngenKostnader(forventetBegrunnelse)
            val hendelseSlot = slot<HendelseType>()
            every { distribuerEndringService.produceHendelse(any(), any(), any(), capture(hendelseSlot)) } just Runs

            // Act
            processGodkjentPrisEndring(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)

            // Assert
            (hendelseSlot.captured as HendelseType.EnkeltplassGodkjennPrisendring).begrunnelse shouldBe forventetBegrunnelse
        }

        @Test
        fun `kaster unntak og publiserer ikke når begrunnelse mangler`() = runTest {
            // Arrange
            val prisinfoUtenBegrunnelse = PrisinformasjonDto.Anskaffelse(pris = 100)
            every {
                PrisinfoRepoAdapter.hentPrisinfo(testGjennomforingId, PrisinfoDbo.Rolle.GJELDENDE)
            } returns prisinfoUtenBegrunnelse

            // Act
            val exception = shouldThrow<IllegalStateException> {
                processGodkjentPrisEndring(consumer, deltakerInTest, totrinnskontrollId, navAnsatt, navEnhet)
            }

            // Assert
            exception.message shouldBe "Fant ikke begrunnelse for prisinfo $totrinnskontrollId"
            verify(exactly = 1) { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }
    }

    private companion object {
        fun lagIngenKostnader(begrunnelse: String = "") = PrisinformasjonDto.IngenKostnader(
            tilleggsopplysninger = "",
            begrunnelse = begrunnelse,
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
