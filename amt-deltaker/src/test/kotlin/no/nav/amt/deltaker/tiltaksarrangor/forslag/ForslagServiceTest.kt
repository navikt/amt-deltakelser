package no.nav.amt.deltaker.tiltaksarrangor.forslag

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorMeldingProducer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.withLogCapture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class ForslagServiceTest {
    private val forslagRepository = ForslagRepository()
    private val arrangorMeldingProducer = mockk<ArrangorMeldingProducer>(relaxed = true)
    private val deltakerService = mockk<DeltakerService>(relaxed = true)
    private val deltakerProducerService = mockk<DeltakerProducerService>(relaxed = true)
    private val navAnsattService = mockk<NavAnsattService>(relaxed = true)
    private val navEnhetService = mockk<NavEnhetService>(relaxed = true)

    private val forslagService = ForslagService(
        forslagRepository = forslagRepository,
        arrangorMeldingProducer = arrangorMeldingProducer,
        deltakerService = deltakerService,
        deltakerProducerService = deltakerProducerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        val navAnsatt = lagNavAnsatt()
        val navEnhet = lagNavEnhet()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<UUID>()) } returns navAnsatt
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(any<String>()) } returns navEnhet
    }

    @Nested
    inner class UpsertAndProduce {
        @Test
        fun `upsertAndProduce - foreldet VenterPaSvar - logger at forslaget ignoreres`() = runTest {
            //  Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val lagretForslag = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
            )
            forslagRepository.upsert(lagretForslag)
            val lagretForslagFraDb = forslagRepository.getOrThrow(lagretForslag.id)
            val foreldetForslag = lagretForslag.copy(status = Forslag.Status.VenterPaSvar)

            // Act & Assert
            withLogCapture("no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService") { logEvents ->
                forslagService.upsertAndProduce(foreldetForslag)

                logEvents.map { it.formattedMessage } shouldBe
                    listOf("Oppdaterte ikke forslag ${foreldetForslag.id} med status VenterPaSvar")
            }

            forslagRepository.getOrThrow(lagretForslag.id) shouldBe lagretForslagFraDb
        }
    }

    @Nested
    inner class AvvisForslag {
        @Test
        fun `avvisForslag - lagrer avvist forslag i database`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)
            every { deltakerService.getOrThrow(deltaker.id) } returns deltaker

            val begrunnelse = "Avslatt fordi det er ikke relevant"

            // Act
            forslagService.avvisForslag(forslag.id, begrunnelse, "Z123", "ENHET")

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            assertSoftly {
                lagredeForslag.id shouldBe forslag.id
                lagredeForslag.status shouldNotBe forslag.status

                assertSoftly(lagredeForslag.status.shouldBeInstanceOf<Forslag.Status.Avvist>()) {
                    begrunnelseFraNav shouldBe begrunnelse
                }
            }
        }

        @Test
        fun `avvisForslag - endrer status fra VenterPaSvar til Avvist`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.VenterPaSvar,
            )
            forslagRepository.upsert(forslag)
            every { deltakerService.getOrThrow(deltaker.id) } returns deltaker

            // Act
            forslagService.avvisForslag(forslag.id, "Begrunnelse", "Z123", "ENHET")

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            lagredeForslag.status as? Forslag.Status.Avvist shouldNotBe null
        }

        @Test
        fun `avvisForslag - setter avvist tidspunkt til naa`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)
            every { deltakerService.getOrThrow(deltaker.id) } returns deltaker
            val tidFor = LocalDateTime.now()

            // Act
            forslagService.avvisForslag(forslag.id, "Begrunnelse", "Z123", "ENHET")

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            val avvistForslag = lagredeForslag.status as Forslag.Status.Avvist
            avvistForslag.avvist.isBefore(LocalDateTime.now().plusSeconds(1)) shouldBe true
            avvistForslag.avvist.isAfter(tidFor.minusSeconds(1)) shouldBe true
        }
    }

    @Nested
    inner class GodkjennForslag {
        @Test
        fun `godkjennForslag - lagrer godkjent forslag i database`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)

            val ansattId = UUID.randomUUID()
            val enhetId = UUID.randomUUID()

            // Act
            forslagService.godkjennForslag(forslag.id, ansattId, enhetId)

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            assertSoftly {
                lagredeForslag.id shouldBe forslag.id
                lagredeForslag.status shouldNotBe forslag.status
                lagredeForslag.status as? Forslag.Status.Godkjent shouldNotBe null
                (lagredeForslag.status as Forslag.Status.Godkjent).let { godkjent ->
                    godkjent.godkjentAv.id shouldBe ansattId
                    godkjent.godkjentAv.enhetId shouldBe enhetId
                }
            }
        }

        @Test
        fun `godkjennForslag - endrer status fra VenterPaSvar til Godkjent`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.VenterPaSvar,
            )
            forslagRepository.upsert(forslag)

            // Act
            forslagService.godkjennForslag(forslag.id, UUID.randomUUID(), UUID.randomUUID())

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            lagredeForslag.status as? Forslag.Status.Godkjent shouldNotBe null
        }

        @Test
        fun `godkjennForslag - setter godkjent tidspunkt til naa`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)
            val tidFor = LocalDateTime.now()

            // Act
            forslagService.godkjennForslag(forslag.id, UUID.randomUUID(), UUID.randomUUID())

            // Assert
            val lagredeForslag = forslagRepository.getOrThrow(forslag.id)
            val godkjentForslag = lagredeForslag.status as Forslag.Status.Godkjent
            godkjentForslag.godkjent.isBefore(LocalDateTime.now().plusSeconds(1)) shouldBe true
            godkjentForslag.godkjent.isAfter(tidFor.minusSeconds(1)) shouldBe true
        }

        @Test
        fun `godkjennForslag - returnerer godkjent forslag`() = runTest {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)
            val ansattId = UUID.randomUUID()
            val enhetId = UUID.randomUUID()

            // Act
            val resultat = forslagService.godkjennForslag(forslag.id, ansattId, enhetId)

            // Assert
            assertSoftly {
                resultat.id shouldBe forslag.id
                resultat.status as? Forslag.Status.Godkjent shouldNotBe null
                (resultat.status as Forslag.Status.Godkjent).let { godkjent ->
                    godkjent.godkjentAv.id shouldBe ansattId
                    godkjent.godkjentAv.enhetId shouldBe enhetId
                }
            }
        }
    }
}
