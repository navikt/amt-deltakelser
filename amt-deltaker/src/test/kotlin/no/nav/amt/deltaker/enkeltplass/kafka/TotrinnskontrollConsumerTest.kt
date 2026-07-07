package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class TotrinnskontrollConsumerTest {
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService = mockk<DeltakerService>()
    private val vedtakService = mockk<VedtakService>()

    private val consumer = TotrinnskontrollConsumer(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        vedtakService = vedtakService,
    )

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
            verify(exactly = 0) { deltakerService.upsertAndProduceDeltaker(any(), any(), any()) }
            verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any()) }
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
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltaker)
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit

            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
            } answers {
                @Suppress("UNCHECKED_CAST")
                val beforeUpsert = args[4] as (Deltaker) -> Deltaker
                val updated = beforeUpsert(deltaker)
                updated.status.type shouldBe DeltakerStatus.Type.DELTAR
                updated
            }

            // Act
            consumer.consume(UUID.randomUUID(), godkjentEnkeltplassOkonomiPayload(gjennomforingId))

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }
            verify { vedtakService.godkjentOkonomiFattVedtak(deltaker) }
            verify {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
            }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_OKONOMI ignoreres`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()

            // Act
            consumer.consume(UUID.randomUUID(), avvistEnkeltplassOkonomiPayload(gjennomforingId))

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }
    }

    @Nested
    inner class ProcessGodkjentTotrinnskontrollTest {
        @Test
        fun `processGodkjentTotrinnskontroll - gjør ingenting når deltaker ikke har status SOKT_INN`() {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltaker)

            // Act
            consumer.processGodkjentInnsoking(gjennomforingId)

            // Assert
            verify(exactly = 0) {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    beforeUpsert = any(),
                )
            }
            verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any()) }
        }

        @Test
        fun `processGodkjentTotrinnskontroll - oppdaterer og publiserer når deltaker har status SOKT_INN`() {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltaker)
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit

            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
            } answers {
                @Suppress("UNCHECKED_CAST")
                val beforeUpsert = args[4] as (Deltaker) -> Deltaker
                val updated = beforeUpsert(deltaker)
                updated.status.type shouldBe DeltakerStatus.Type.DELTAR
                updated
            }

            // Act
            consumer.processGodkjentInnsoking(gjennomforingId)

            // Assert
            verify { vedtakService.godkjentOkonomiFattVedtak(deltaker) }
            verify {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
            }
        }

        @Test
        fun `processGodkjentTotrinnskontroll - setter VENTER_PA_OPPSTART når startdato er i fremtiden`() {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = LocalDate.now().plusDays(2),
                sluttdato = LocalDate.now().plusWeeks(4),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltaker)
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } returns Unit

            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
            } answers {
                @Suppress("UNCHECKED_CAST")
                val beforeUpsert = args[4] as (Deltaker) -> Deltaker
                val updated = beforeUpsert(deltaker)
                updated.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                updated
            }

            // Act
            consumer.processGodkjentInnsoking(gjennomforingId)

            // Assert
            verify { vedtakService.godkjentOkonomiFattVedtak(deltaker) }
            verify {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = deltaker,
                    erDeltakerSluttdatoEndret = false,
                    beforeUpsert = any(),
                )
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
    }
}
