package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TotrinnskontrollConsumerTest {
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService = mockk<DeltakerService>()
    private val vedtakService = mockk<VedtakService>()
    private val distribuerEndringService = mockk<DistribuerEndringService>()
    private val navAnsattRepository = mockk<NavAnsattRepository>()
    private val navEnhetRepository = mockk<NavEnhetRepository>()

    private val consumer = TotrinnskontrollConsumer(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        vedtakService = vedtakService,
        distribuerEndringService = distribuerEndringService,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
    )

    @BeforeEach
    fun setup() {
        mockkObject(PrisinfoRepoAdapter)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(PrisinfoRepoAdapter)
    }

    @Nested
    inner class ConsumeTest {
        @Test
        fun `consume - kaster unntak ved tombstone`() = runTest {
            // Arrange
            val key = UUID.randomUUID()

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                consumer.consume(key, null)
            }

            // Assert
            exception.message shouldBe "Tombstone er ikke støttet. Key: $key"
        }

        @Test
        fun `consume - ignorerer meldinger med ukjent type`() = runTest {
            // Arrange
            val rawJson =
                """
                {
                  "id": "83f1a9e0-8282-4552-87ea-a1c163f10df6",
                  "entityId": "4c38feef-0ef5-4582-b9d0-66cd33de0b2b",
                  "type": "UTBETALING_LINJE_OPPRETTELSE",
                  "behandletAv": "L164122",
                  "behandletTidspunkt": "2026-05-11T15:13:21.311216Z",
                  "besluttetAv": null,
                  "besluttetTidspunkt": null,
                  "aarsaker": [],
                  "forklaring": null
                }
                """.trimIndent()

            // Act
            consumer.consume(UUID.fromString("4c38feef-0ef5-4582-b9d0-66cd33de0b2b"), rawJson)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }

        @Test
        fun `consume - ignorerer ukjent type uten aa kaste unntak`() = runTest {
            // Arrange
            val rawJson =
                """
                {
                  "id": "83f1a9e0-8282-4552-87ea-a1c163f10df6",
                  "entityId": "4c38feef-0ef5-4582-b9d0-66cd33de0b2b",
                  "type": "EN_HELT_NY_TYPE_VI_IKKE_KJENNER",
                  "behandletAv": { "noeHeltAnnet": true }
                }
                """.trimIndent()

            // Act
            consumer.consume(UUID.randomUUID(), rawJson)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI prosesseres`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every {
                deltakerRepository.getEnkeltplassdeltaker(gjennomforingId)
            } returns Result.success(deltakerInTest)

            every {
                PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                    gjennomforingId = any(),
                    prisinfoId = any(),
                )
            } returns true
            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit
            every { navAnsattRepository.getOrThrow(any<UUID>()) } returns mockk()
            every { navEnhetRepository.getOrThrow(any<UUID>()) } returns mockk()
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } returns Unit

            val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = capture(beforeUpsertSlot),
                    afterUpsert = any(),
                )
            } returns deltakerInTest.copy(
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
            )

            // Act
            consumer.consume(
                totrinnskontrollId = UUID.randomUUID(),
                value = godkjentEnkeltplassOkonomiPayload(gjennomforingId),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }

            val updated = beforeUpsertSlot.captured(deltakerInTest)
            updated.status.type shouldBe DeltakerStatus.Type.DELTAR
            verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest) }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_OKONOMI ignoreres`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()

            // Act
            consumer.consume(
                totrinnskontrollId = UUID.randomUUID(),
                value = avvistEnkeltplassOkonomiPayload(gjennomforingId),
            )

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI ignoreres når prisinfo ikke finnes`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every {
                deltakerRepository.getEnkeltplassdeltaker(gjennomforingId)
            } returns Result.success(deltakerInTest)

            every {
                PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                    gjennomforingId = any(),
                    prisinfoId = any(),
                )
            } returns false

            // Act
            consumer.consume(UUID.randomUUID(), godkjentEnkeltplassOkonomiPayload(gjennomforingId))

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }
            verify(exactly = 0) {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = any(),
                    afterUpsert = any(),
                )
            }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING prosesseres`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every { PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(any(), any()) } returns true
            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit

            // Act
            consumer.consume(UUID.randomUUID(), godkjentEnkeltplassPrisinformasjonPayload(gjennomforingId))

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }
            verify { PrisinfoRepoAdapter.godkjennOkonomi(any()) }
        }

        @Test
        fun `consume - status ENKELTPLASS_OKONOMI ignoreres`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()

            // Act
            consumer.consume(UUID.randomUUID(), avvistEnkeltplassOkonomiPayload(gjennomforingId))

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }
    }

    @Nested
    inner class ProcessGodkjentInnsokingTest {
        @Test
        fun `processGodkjentInnsoking - gjør ingenting når deltaker ikke har status SOKT_INN`() {
            // Arrange
            val deltakerInTest = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))

            // Act
            consumer.processGodkjentInnsoking(deltakerInTest)

            // Assert
            verify(exactly = 0) {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = any(),
                    afterUpsert = any(),
                )
            }
            verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any()) }
        }

        @Test
        fun `processGodkjentInnsoking - oppdaterer deltaker status via beforeUpsert`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit
            every { navAnsattRepository.getOrThrow(any<UUID>()) } returns mockk()
            every { navEnhetRepository.getOrThrow(any<UUID>()) } returns mockk()
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } returns Unit

            val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = capture(beforeUpsertSlot),
                    afterUpsert = any(),
                )
            } returns deltakerInTest.copy(
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
            )

            // Act
            consumer.processGodkjentInnsoking(deltakerInTest)

            // Assert
            val updated = beforeUpsertSlot.captured(deltakerInTest)
            updated.status.type shouldBe DeltakerStatus.Type.DELTAR
            verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest) }
        }

        @Test
        fun `processGodkjentInnsoking - afterUpsert produserer hendelse`() {
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
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
                vedtaksinformasjon = vedtaksinformasjon,
            )
            val navAnsatt = mockk<NavAnsatt>()
            val navEnhet = mockk<NavEnhet>()

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit
            every { navAnsattRepository.getOrThrow(sistEndretAv) } returns navAnsatt
            every { navEnhetRepository.getOrThrow(sistEndretAvEnhet) } returns navEnhet
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } returns Unit

            val afterUpsertSlot = slot<(Deltaker) -> Unit>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = any(),
                    afterUpsert = capture(afterUpsertSlot),
                )
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(deltakerInTest)
            afterUpsertSlot.captured(deltakerInTest)

            // Assert
            verify { navAnsattRepository.getOrThrow(sistEndretAv) }
            verify { navEnhetRepository.getOrThrow(sistEndretAvEnhet) }
            verify {
                distribuerEndringService.produceHendelseForUtkast(
                    deltaker = deltakerInTest,
                    navAnsatt = navAnsatt,
                    enhet = navEnhet,
                    block = any(),
                )
            }
        }

        @Test
        fun `processGodkjentInnsoking - afterUpsert kaster feil når vedtaksinformasjon mangler`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
                vedtaksinformasjon = null,
            )

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit

            val afterUpsertSlot = slot<(Deltaker) -> Unit>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = any(),
                    afterUpsert = capture(afterUpsertSlot),
                )
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(deltakerInTest)

            // Assert
            val exception = shouldThrow<IllegalStateException> {
                afterUpsertSlot.captured(deltakerInTest)
            }
            exception.message shouldBe
                "Kan ikke produsere hendelse for økonomi godkjent for deltaker ${deltakerInTest.id} uten vedtak"
        }

        @Test
        fun `processGodkjentInnsoking - kaster unntak når godkjennOkonomi feiler`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )
            val exception = RuntimeException("Database error")

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } throws exception

            // Act & Assert
            shouldThrow<RuntimeException> {
                consumer.processGodkjentInnsoking(deltakerInTest)
            }
        }
    }

    @Nested
    inner class ProcessGodkjentPrisinformasjonTest {
        @Test
        fun `processGodkjentPrisinformasjon - kaller godkjennOkonomi med gjennomforingId`() {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltaker = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
            )

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } returns Unit

            // Act
            consumer.processGodkjentPrisinformasjon(deltaker)

            // Assert
            verify { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId) }
        }

        @Test
        fun `processGodkjentPrisinformasjon - kaster unntak når godkjennOkonomi feiler`() {
            // Arrange
            val deltaker = lagDeltaker()
            val exception = RuntimeException("Database error")

            every { PrisinfoRepoAdapter.godkjennOkonomi(any()) } throws exception

            // Act & Assert
            shouldThrow<RuntimeException> {
                consumer.processGodkjentPrisinformasjon(deltaker)
            }
        }
    }

    @Nested
    inner class NyDeltakerStatusTest {
        @Test
        fun `nyDeltakerStatus - returnerer FULLFORT når sluttdato er i fortiden`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now().minusWeeks(2),
                sluttdato = LocalDate.now().minusDays(1),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.FULLFORT
        }

        @Test
        fun `nyDeltakerStatus - returnerer VENTER_PA_OPPSTART når startdato er i fremtiden`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now().plusDays(5),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når startdato er idag`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når startdato er i fortiden og sluttdato er i fremtiden`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now().minusDays(3),
                sluttdato = LocalDate.now().plusWeeks(2),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når sluttdato er idag`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now().minusDays(5),
                sluttdato = LocalDate.now(),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - kaster feil når startdato mangler`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = null,
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            // Act & Assert
            shouldThrow<IllegalStateException> {
                TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
            }
        }

        @Test
        fun `nyDeltakerStatus - kaster feil når sluttdato mangler`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                startdato = LocalDate.now(),
                sluttdato = null,
            )

            // Act & Assert
            shouldThrow<IllegalStateException> {
                TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
            }
        }
    }

    @Nested
    inner class SkalBehandleTotrinnskontrollHendelseTest {
        @Test
        fun `skalBehandleTotrinnskontrollHendelse - returnerer true for ENKELTPLASS_OKONOMI`() {
            // Arrange
            val payload = """{"type":"ENKELTPLASS_OKONOMI"}"""

            // Act + Assert
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe true
        }

        @Test
        fun `skalBehandleTotrinnskontrollHendelse - returnerer false for andre typer`() {
            // Arrange
            val payload = """{"type":"UTBETALING_LINJE_OPPRETTELSE"}"""

            // Act + Assert
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe false
        }

        @Test
        fun `skalBehandleTotrinnskontrollHendelse - returnerer true for ENKELTPLASS_PRISENDRING`() {
            // Arrange
            val payload = """{"type":"ENKELTPLASS_PRISENDRING"}"""

            // Act + Assert
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe true
        }

        @Test
        fun `skalBehandleTotrinnskontrollHendelse - returnerer false når type mangler`() {
            // Arrange
            val payload = """{"id":"test"}"""

            // Act + Assert
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe false
        }

        @Test
        fun `skalBehandleTotrinnskontrollHendelse - returnerer false for ukjent type`() {
            // Arrange
            val payload = """{"type":"COMPLETELY_UNKNOWN_TYPE"}"""

            // Act + Assert
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe false
        }
    }

    companion object {
        private fun godkjentEnkeltplassOkonomiPayload(gjennomforingId: UUID): String =
            """
            {
              "id": "${UUID.randomUUID()}",
              "entityId": "$gjennomforingId",
              "type": "ENKELTPLASS_OKONOMI",
              "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
              "behandletTidspunkt": "2026-06-01T10:00:00Z",
              "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
              "besluttetTidspunkt": "2026-06-01T10:01:00Z",
              "status": "GODKJENT",
              "aarsaker": [],
              "forklaring": null
            }
            """.trimIndent()

        private fun avvistEnkeltplassOkonomiPayload(gjennomforingId: UUID): String =
            """
            {
              "id": "${UUID.randomUUID()}",
              "entityId": "$gjennomforingId",
              "type": "ENKELTPLASS_OKONOMI",
              "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
              "behandletTidspunkt": "2026-06-01T10:00:00Z",
              "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
              "besluttetTidspunkt": "2026-06-01T10:01:00Z",
              "status": "RETURNERT",
              "aarsaker": ["MANGLER_DOKUMENTASJON"],
              "forklaring": "Ikke godkjent",
              "totrinnskontroll": {
                "id": "${UUID.randomUUID()}",
                "behandletAv": "VEILEDER"
              }
            }
            """.trimIndent()

        private fun godkjentEnkeltplassPrisinformasjonPayload(gjennomforingId: UUID): String =
            """
            {
              "id": "${UUID.randomUUID()}",
              "entityId": "$gjennomforingId",
              "type": "ENKELTPLASS_PRISENDRING",
              "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
              "behandletTidspunkt": "2026-06-01T10:00:00Z",
              "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
              "besluttetTidspunkt": "2026-06-01T10:01:00Z",
              "status": "GODKJENT",
              "aarsaker": [],
              "forklaring": null
            }
            """.trimIndent()
    }
}
