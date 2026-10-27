package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TotrinnskontrollConsumerTest : TotrinnskontrollConsumerTestBase() {
    @BeforeEach
    fun setup() {
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns true
        every { PrisinfoRepoAdapter.hentPrisinfo(any<UUID>(), any<PrisinfoDbo.Rolle>()) } returns PrisinformasjonDto.IngenKostnader(
            tilleggsopplysninger = "",
        )

        coEvery {
            navAnsattService.hentNavAnsattOgEnhet(any<String>())
        } returns Pair(navAnsattInTest, navEnhetInTest)
        every {
            distribuerEndringService.produceHendelse(any(), any(), any(), any())
        } just Runs
        every { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) } just Runs
        every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs
    }

    @Nested
    inner class ConsumeTest {
        @BeforeEach
        fun setup() {
            every { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs
        }

        @Test
        fun `consume - kaster unntak ved tombstone`() = runTest {
            // Arrange
            val key = totrinnskontrollId

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                consumer.consume(key, null)
            }

            // Assert
            exception.message shouldBe "Tombstone er ikke støttet. Key: $totrinnskontrollId"
        }

        @Test
        fun `consume - ignorerer meldinger med ukjent type`() = runTest {
            // Arrange
            val rawJson =
                """
                {
                  "id": "$totrinnskontrollId",
                  "entityId": "$gjennomforingId",
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
            consumer.consume(totrinnskontrollId, rawJson)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltakerOrThrow(any()) }
        }

        @Test
        fun `consume - ignorerer ukjent type uten aa kaste unntak`() = runTest {
            // Arrange
            val rawJson =
                """
                {
                  "id": "$totrinnskontrollId",
                  "entityId": "$gjennomforingId",
                  "type": "EN_HELT_NY_TYPE_VI_IKKE_KJENNER",
                  "behandletAv": { "noeHeltAnnet": true }
                }
                """.trimIndent()

            // Act
            consumer.consume(totrinnskontrollId, rawJson)

            // Assert
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltakerOrThrow(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI prosesseres`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.SOKT_INN,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)

            val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
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
                key = UUID.randomUUID(),
                value = godkjentEnkeltplassOkonomiPayload(gjennomforingId),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltakerOrThrow(gjennomforingId) }

            val updated = beforeUpsertSlot.captured(deltakerInTest)
            updated.status.type shouldBe DeltakerStatus.Type.DELTAR
            verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest, any(), any()) }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_OKONOMI oppdaterer status`() = runTest {
            // Arrange
            every { PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(any(), any()) } returns 1

            // Act
            consumer.consume(
                key = UUID.randomUUID(),
                value = avvistEnkeltplassOkonomiPayload(gjennomforingId),
            )

            // Assert
            verify {
                PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(
                    prisinformasjonId = any(),
                    status = PrisinfoDbo.TotrinnskontrollStatus.RETURNERT,
                )
            }
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltakerOrThrow(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING prosesseres`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                gjennomforingId,
            )
            val godkjentPrisinfo = PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
                begrunnelse = "Testbegrunnelse",
            )
            val hendelseSlot = slot<HendelseType>()

            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)
            stubGjeldendePrisinfo(gjennomforingId, godkjentPrisinfo)
            every { distribuerEndringService.produceHendelse(any(), any(), any(), capture(hendelseSlot)) } just Runs

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltakerOrThrow(gjennomforingId) }
            verify { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId, navAnsattInTest.id, navEnhetInTest.id) }
            verify { distribuerEndringService.produceHendelse(deltakerInTest, navAnsattInTest, navEnhetInTest, any()) }
            hendelseSlot.captured shouldBe HendelseType.EnkeltplassGodkjennPrisendring(
                prisinfo = godkjentPrisinfo,
                begrunnelse = "Testbegrunnelse",
            )
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING attribuerer godkjenning til besluttetAv`() = runTest {
            // Arrange - behandletAv (Z123456) og besluttetAv (Z654321) er ulike personer
            val besluttetEnhet = lagNavEnhet()
            val besluttetAnsatt = lagNavAnsatt(navEnhetId = besluttetEnhet.id)
            coEvery { navAnsattService.hentNavAnsattOgEnhet("Z123456") } returns Pair(navAnsattInTest, navEnhetInTest)
            coEvery { navAnsattService.hentNavAnsattOgEnhet("Z654321") } returns Pair(besluttetAnsatt, besluttetEnhet)

            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)
            stubGjeldendePrisinfo(
                gjennomforingId,
                PrisinformasjonDto.IngenKostnader(
                    tilleggsopplysninger = "",
                ),
            )

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert - godkjenning attribueres til besluttetAv, hendelsen til behandletAv
            verify {
                PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId, besluttetAnsatt.id, besluttetEnhet.id)
            }
            verify { distribuerEndringService.produceHendelse(deltakerInTest, navAnsattInTest, navEnhetInTest, any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING feiler når besluttetAv er systembruker`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)
            stubGjeldendePrisinfo(
                gjennomforingId,
                PrisinformasjonDto.IngenKostnader(
                    tilleggsopplysninger = "",
                ),
            )

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                consumer.consume(
                    key = totrinnskontrollId,
                    value = godkjentEnkeltplassPrisinformasjonPayload(
                        gjennomforingId = gjennomforingId,
                        totrinnskontrollId = totrinnskontrollId,
                        besluttetAv =
                            """
                            {
                              "type": "SYSTEM",
                              "system": "maskinell godkjenning"
                            }
                            """.trimIndent(),
                    ),
                )
            }

            // Assert
            exception.message shouldBe
                "Totrinnskontroll $totrinnskontrollId er godkjent med uventet type for `besluttetAv`. Avbryter behandling: System(system=maskinell godkjenning)"
            verify(exactly = 0) {
                PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any())
            }
            verify(exactly = 0) {
                distribuerEndringService.produceHendelse(any(), any(), any(), any())
            }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING for SOKT_INN prosesseres som innsoking`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.SOKT_INN,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)
            stubGodkjennOkonomi(true)

            val beforeUpsertSlot = slot<(Deltaker) -> Deltaker>()
            val afterUpsertSlot = slot<(Deltaker) -> Unit>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = capture(beforeUpsertSlot),
                    afterUpsert = capture(afterUpsertSlot),
                )
            } answers {
                val updated = beforeUpsertSlot.captured(firstArg<Deltaker>())
                afterUpsertSlot.captured(updated)
                updated
            }

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify(exactly = 1) {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = false,
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = any(),
                    afterUpsert = any(),
                )
            }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
            verify(exactly = 1) { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING skipper historisk prisinfo`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(deltakerRepository, gjennomforingId, deltakerInTest)
            stubGodkjennOkonomi(false)

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltakerOrThrow(gjennomforingId) }
            verify { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId, navAnsattInTest.id, navEnhetInTest.id) }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI skipper naar prisinfo allerede er godkjent`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.SOKT_INN,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(
                deltakerRepository,
                gjennomforingId,
                deltakerInTest,
                PrisinfoDbo.TotrinnskontrollStatus.GODKJENT,
            )

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify(exactly = 0) {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = gjennomforingId,
                    prisinformasjonId = totrinnskontrollId,
                    godkjentAv = any(),
                    godkjentAvEnhet = any(),
                )
            }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING skipper naar prisinfoSomVenter er false`() = runTest {
            // Arrange
            val deltakerInTest = lagEnkeltplassDeltaker(
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                gjennomforingId,
            )
            stubEnkeltplassDeltaker(
                deltakerRepository,
                gjennomforingId,
                deltakerInTest,
                PrisinfoDbo.TotrinnskontrollStatus.GODKJENT,
            )

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify(exactly = 0) {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = any(),
                    prisinformasjonId = any(),
                    godkjentAv = any(),
                    godkjentAvEnhet = any(),
                )
            }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_PRISENDRING oppdaterer status`() = runTest {
            // Arrange
            every { PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(any(), any()) } returns 1

            // Act
            consumer.consume(
                key = UUID.randomUUID(),
                value = avvistEnkeltplassPrisinformasjonPayload(gjennomforingId),
            )

            // Assert
            verify { PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(any(), PrisinfoDbo.TotrinnskontrollStatus.RETURNERT) }
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltakerOrThrow(any()) }
        }
    }

    @Nested
    inner class SkalBehandleTotrinnskontrollHendelseTest {
        @Test
        fun `returnerer true for ENKELTPLASS_OKONOMI`() {
            // Arrange
            val payload = """{"type": "ENKELTPLASS_OKONOMI"}"""

            // Act
            val skalBehandles = consumer.skalBehandleTotrinnskontrollHendelse(payload)

            // Assert
            skalBehandles shouldBe true
        }

        @Test
        fun `returnerer true for ENKELTPLASS_PRISENDRING`() {
            // Arrange
            val payload = """{"type": "ENKELTPLASS_PRISENDRING"}"""

            // Act
            val skalBehandles = consumer.skalBehandleTotrinnskontrollHendelse(payload)

            // Assert
            skalBehandles shouldBe true
        }

        @Test
        fun `returnerer false for ukjent type`() {
            // Arrange
            val payload = """{"type": "TILSAGN_OPPRETTELSE"}"""

            // Act
            val skalBehandles = consumer.skalBehandleTotrinnskontrollHendelse(payload)

            // Assert
            skalBehandles shouldBe false
        }

        @Test
        fun `returnerer false naar type mangler`() {
            // Arrange
            val payload = """{"status": "GODKJENT"}"""

            // Act
            val skalBehandles = consumer.skalBehandleTotrinnskontrollHendelse(payload)

            // Assert
            skalBehandles shouldBe false
        }
    }

    private companion object {
        fun lagEnkeltplassDeltaker(
            status: DeltakerStatus.Type,
            deltakerlisteId: UUID,
            startdato: LocalDate? = LocalDate.now(),
            sluttdato: LocalDate? = LocalDate.now().plusWeeks(4),
            vedtaksinformasjon: Vedtaksinformasjon? = null,
        ): Deltaker = lagDeltaker(
            gjennomforing = lagDeltakerliste(id = deltakerlisteId),
            status = lagDeltakerStatus(status),
            startdato = startdato,
            sluttdato = sluttdato,
            vedtaksinformasjon = vedtaksinformasjon,
        )

        fun stubEnkeltplassDeltaker(
            deltakerRepository: DeltakerRepository,
            gjennomforingId: UUID,
            deltaker: Deltaker,
            totrinnskontrollStatus: PrisinfoDbo.TotrinnskontrollStatus = PrisinfoDbo.TotrinnskontrollStatus.TIL_BEHANDLING,
        ) {
            every { deltakerRepository.getEnkeltplassdeltakerOrThrow(gjennomforingId) } returns deltaker
            every { PrisinfoRepository.hentPrisinfoStatus(any(), any()) } returns totrinnskontrollStatus
        }

        fun stubGodkjennOkonomi(result: Boolean) {
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns result
        }
    }

    private fun stubGjeldendePrisinfo(
        gjennomforingId: UUID,
        prisinfo: PrisinformasjonDto,
    ) {
        every {
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )
        } returns prisinfo
    }

    @Nested
    inner class ProcessGodkjentPrisEndringTest {
        val navEnhet = lagNavEnhet()
        val navAnsatt = lagNavAnsatt(navIdent = "Z999999", navEnhetId = navEnhet.id)

        val gjennomforingId: UUID = UUID.randomUUID()
        val deltakerInTest = lagDeltaker(
            gjennomforing = lagDeltakerliste(id = gjennomforingId),
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
            } returns PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
            )
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = gjennomforingId,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                )
            } returns PrisinformasjonDto.IngenKostnader(
                tilleggsopplysninger = "",
            )
            every { distribuerEndringService.produceHendelse(any(), any(), any(), any()) } just Runs
        }

        @Test
        fun `kaller godkjennOkonomi med gjennomforingId`() = runTest {
            // Act
            consumer.processGodkjentPrisEndring(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                behandletAvNavAnsatt = navAnsatt,
                behandletAvNavEnhet = navEnhet,
                godkjentAvNavAnsatt = navAnsatt,
                godkjentAvNavEnhet = navEnhet,
            )

            // Assert
            verify {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = gjennomforingId,
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

            // Act & Assert
            shouldThrow<RuntimeException> {
                consumer.processGodkjentPrisEndring(
                    deltaker = deltakerInTest,
                    prisinfoId = totrinnskontrollId,
                    behandletAvNavAnsatt = navAnsatt,
                    behandletAvNavEnhet = navEnhet,
                    godkjentAvNavAnsatt = navAnsatt,
                    godkjentAvNavEnhet = navEnhet,
                )
            }
        }

        @Test
        fun `processGodkjentPrisEndring - publiserer ikke når godkjennOkonomi returnerer false`() = runTest {
            // Arrange
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) } returns false

            // Act
            consumer.processGodkjentPrisEndring(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                behandletAvNavAnsatt = navAnsatt,
                behandletAvNavEnhet = navEnhet,
                godkjentAvNavAnsatt = navAnsatt,
                godkjentAvNavEnhet = navEnhet,
            )

            // Assert
            verify { PrisinfoRepoAdapter.godkjennOkonomi(any(), any(), any(), any()) }
            verify(exactly = 0) { PrisinfoRepoAdapter.hentPrisinfo(any<UUID>(), PrisinfoDbo.Rolle.GJELDENDE) }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }
    }

    fun godkjentEnkeltplassOkonomiPayload(gjennomforingId: UUID): String =
        """
        {
          "id": "${UUID.randomUUID()}",
          "entityId": "$gjennomforingId",
          "type": "ENKELTPLASS_OKONOMI",
          "status": "GODKJENT",
          "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
          "behandletTidspunkt": "2026-06-01T10:00:00Z",
          "behandletBegrunnelse": null,
          "behandletAarsaker": [],
          "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
          "besluttetTidspunkt": "2026-06-01T10:01:00Z",
          "besluttetBegrunnelse": null,
          "besluttetAarsaker": []
        }
        """.trimIndent()

    fun avvistEnkeltplassOkonomiPayload(gjennomforingId: UUID): String =
        """
        {
          "id": "${UUID.randomUUID()}",
          "entityId": "$gjennomforingId",
          "type": "ENKELTPLASS_OKONOMI",
          "status": "RETURNERT",
          "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
          "behandletTidspunkt": "2026-06-01T10:00:00Z",
          "behandletBegrunnelse": null,
          "behandletAarsaker": [],
          "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
          "besluttetTidspunkt": "2026-06-01T10:01:00Z",
          "besluttetBegrunnelse": "Ikke godkjent",
          "besluttetAarsaker": ["MANGLER_DOKUMENTASJON"]
        }
        """.trimIndent()

    fun avvistEnkeltplassPrisinformasjonPayload(gjennomforingId: UUID): String =
        """
        {
          "id": "${UUID.randomUUID()}",
          "entityId": "$gjennomforingId",
          "type": "ENKELTPLASS_PRISENDRING",
          "status": "RETURNERT",
          "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
          "behandletTidspunkt": "2026-06-01T10:00:00Z",
          "behandletBegrunnelse": null,
          "behandletAarsaker": [],
          "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
          "besluttetTidspunkt": "2026-06-01T10:01:00Z",
          "besluttetBegrunnelse": "Ikke godkjent",
          "besluttetAarsaker": ["MANGLER_DOKUMENTASJON"]
        }
        """.trimIndent()

    fun godkjentEnkeltplassPrisinformasjonPayload(
        gjennomforingId: UUID,
        totrinnskontrollId: UUID,
        besluttetAv: String =
            """
            {
              "type": "NAV_ANSATT",
              "navIdent": "Z654321"
            }
            """.trimIndent(),
    ): String =
        """
        {
          "id": "$totrinnskontrollId",
          "entityId": "$gjennomforingId",
          "type": "ENKELTPLASS_PRISENDRING",
          "status": "GODKJENT",
          "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
          "behandletTidspunkt": "2026-06-01T10:00:00Z",
          "behandletBegrunnelse": null,
          "behandletAarsaker": [],
          "besluttetAv": $besluttetAv,
          "besluttetTidspunkt": "2026-06-01T10:01:00Z",
          "besluttetBegrunnelse": null,
          "besluttetAarsaker": []
        }
        """.trimIndent()
}
