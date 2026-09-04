package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
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
    private val navAnsattService = mockk<NavAnsattService>(relaxed = true)
    private val navEnhetService = mockk<NavEnhetService>(relaxed = true)

    private val consumer = TotrinnskontrollConsumer(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        vedtakService = vedtakService,
        distribuerEndringService = distribuerEndringService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )

    val gjennomforingId: UUID = UUID.randomUUID()
    val totrinnskontrollId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        val navEnhetId = UUID.randomUUID()
        mockkObject(Database)
        every { Database.transaction<Any>(any()) } answers {
            firstArg<() -> Any>().invoke()
        }
        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns true
        every { PrisinfoRepoAdapter.hentPrisinfo(any<UUID>(), any<PrisinfoDbo.Rolle>()) } returns PrisinformasjonDto.IngenKostnader(
            aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = null,
        )
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<String>()) } returns NavAnsatt(
            id = UUID.randomUUID(),
            navIdent = "Z654321",
            navn = "Saksbehandler",
            epost = null,
            telefon = null,
            navEnhetId = navEnhetId,
        )
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(navEnhetId) } returns NavEnhet(
            id = navEnhetId,
            enhetsnummer = "1234",
            navn = "NAV Test",
        )
        every { distribuerEndringService.produceHendelse(any(), any(), any(), any()) } just Runs
        mockkObject(PrisinfoRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Database)
        unmockkObject(PrisinfoRepoAdapter)
        unmockkObject(PrisinfoRepository)
    }

    @Nested
    inner class ConsumeTest {
        @Test
        fun `consume - kaster unntak ved tombstone`() = runTest {
            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                consumer.consume(totrinnskontrollId, null)
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
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
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
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI prosesseres`() = runTest {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )

            every {
                deltakerRepository.getEnkeltplassdeltaker(gjennomforingId)
            } returns Result.success(deltakerInTest)

            every {
                PrisinfoRepository.hentPrisinfoStatus(any(), any())
            } returns PrisinfoDbo.PrisinfoStatus.TIL_BEHANDLING
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs

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
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }

            val updated = beforeUpsertSlot.captured(deltakerInTest)
            updated.status.type shouldBe DeltakerStatus.Type.DELTAR
            verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest) }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_OKONOMI oppdaterer status`() = runTest {
            // Arrange
            every { PrisinfoRepository.oppdaterStatus(any(), any()) } returns 1

            // Act
            consumer.consume(
                key = UUID.randomUUID(),
                value = avvistEnkeltplassOkonomiPayload(gjennomforingId),
            )

            // Assert
            verify {
                PrisinfoRepository.oppdaterStatus(
                    prisinformasjonId = any(),
                    status = PrisinfoDbo.PrisinfoStatus.RETURNERT,
                )
            }
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING prosesseres`() = runTest {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )
            val godkjentPrisinfo = PrisinformasjonDto.IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )
            val navEnhetId = UUID.randomUUID()
            val navAnsatt = NavAnsatt(
                id = UUID.randomUUID(),
                navIdent = "Z654321",
                navn = "Saksbehandler",
                epost = null,
                telefon = null,
                navEnhetId = navEnhetId,
            )
            val navEnhet = NavEnhet(
                id = navEnhetId,
                enhetsnummer = "1234",
                navn = "NAV Test",
            )
            val hendelseSlot = slot<HendelseType>()

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every {
                PrisinfoRepository.hentPrisinfoStatus(any(), any())
            } returns PrisinfoDbo.PrisinfoStatus.TIL_BEHANDLING
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = gjennomforingId,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                )
            } returns godkjentPrisinfo
            every { distribuerEndringService.produceHendelse(any(), any(), any(), capture(hendelseSlot)) } just Runs
            coEvery { navAnsattService.hentEllerOpprettNavAnsatt("Z654321") } returns navAnsatt
            coEvery { navEnhetService.hentEllerOpprettNavEnhet(navEnhetId) } returns navEnhet

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }
            verify { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId) }
            verify { distribuerEndringService.produceHendelse(deltakerInTest, navAnsatt, navEnhet, any()) }
            hendelseSlot.captured shouldBe HendelseType.EnkeltplassGodkjennPrisendring(
                prisinfo = godkjentPrisinfo,
            )
            coVerify { navAnsattService.hentEllerOpprettNavAnsatt("Z654321") }
            coVerify { navEnhetService.hentEllerOpprettNavEnhet(navEnhetId) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING for SOKT_INN prosesseres som innsoking`() = runTest {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every { PrisinfoRepository.hentPrisinfoStatus(any(), any()) } returns PrisinfoDbo.PrisinfoStatus.TIL_BEHANDLING
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns true
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs

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
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every {
                PrisinfoRepository.hentPrisinfoStatus(any(), any())
            } returns PrisinfoDbo.PrisinfoStatus.TIL_BEHANDLING
            every { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId) } returns false

            // Act
            consumer.consume(
                key = totrinnskontrollId,
                value = godkjentEnkeltplassPrisinformasjonPayload(
                    gjennomforingId = gjennomforingId,
                    totrinnskontrollId = totrinnskontrollId,
                ),
            )

            // Assert
            verify { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) }
            verify { PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingId, totrinnskontrollId) }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING kaster unntak naar besluttetAv mangler`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every { PrisinfoRepository.hentPrisinfoStatus(any(), any()) } returns PrisinfoDbo.PrisinfoStatus.TIL_BEHANDLING

            val rawJson =
                """
                {
                  "id": "$totrinnskontrollId",
                  "entityId": "$gjennomforingId",
                  "type": "ENKELTPLASS_PRISENDRING",
                  "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
                  "behandletTidspunkt": "2026-06-01T10:00:00Z",
                  "besluttetAv": null,
                  "besluttetTidspunkt": null,
                  "status": "GODKJENT",
                  "aarsaker": [],
                  "forklaring": null
                }
                """.trimIndent()

            // Act + Assert
            val exception = shouldThrow<IllegalStateException> {
                consumer.consume(
                    key = totrinnskontrollId,
                    value = rawJson,
                )
            }
            exception.message shouldBe "BesluttetAv mangler for ENKELTPLASS_PRISENDRING"
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_OKONOMI skipper naar prisinfo allerede er godkjent`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )

            every {
                deltakerRepository.getEnkeltplassdeltaker(gjennomforingId)
            } returns Result.success(deltakerInTest)
            every {
                PrisinfoRepository.hentPrisinfoStatus(any(), any())
            } returns PrisinfoDbo.PrisinfoStatus.GODKJENT

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
                )
            }
        }

        @Test
        fun `consume - godkjent ENKELTPLASS_PRISENDRING skipper naar prisinfoSomVenter er false`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            every { deltakerRepository.getEnkeltplassdeltaker(gjennomforingId) } returns Result.success(deltakerInTest)
            every {
                PrisinfoRepository.hentPrisinfoStatus(any(), any())
            } returns PrisinfoDbo.PrisinfoStatus.GODKJENT

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
                )
            }
        }

        @Test
        fun `consume - avvist ENKELTPLASS_PRISENDRING oppdaterer status`() = runTest {
            // Arrange
            every { PrisinfoRepository.oppdaterStatus(any(), any()) } returns 1

            // Act
            consumer.consume(
                key = UUID.randomUUID(),
                value = avvistEnkeltplassPrisinformasjonPayload(gjennomforingId),
            )

            // Assert
            verify { PrisinfoRepository.oppdaterStatus(any(), PrisinfoDbo.PrisinfoStatus.RETURNERT) }
            verify(exactly = 0) { deltakerRepository.getEnkeltplassdeltaker(any()) }
        }
    }

    @Nested
    inner class SkalBehandleTotrinnskontrollHendelseTest {
        @Test
        fun `returnerer true for ENKELTPLASS_OKONOMI`() {
            val payload = """{"type": "ENKELTPLASS_OKONOMI"}"""
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe true
        }

        @Test
        fun `returnerer true for ENKELTPLASS_PRISENDRING`() {
            val payload = """{"type": "ENKELTPLASS_PRISENDRING"}"""
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe true
        }

        @Test
        fun `returnerer false for ukjent type`() {
            val payload = """{"type": "TILSAGN_OPPRETTELSE"}"""
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe false
        }

        @Test
        fun `returnerer false naar type mangler`() {
            val payload = """{"status": "GODKJENT"}"""
            consumer.skalBehandleTotrinnskontrollHendelse(payload) shouldBe false
        }
    }

    @Nested
    inner class ProcessGodkjentInnsokingTest {
        @Test
        fun `processGodkjentInnsoking - gjør ingenting når deltaker ikke har status SOKT_INN`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

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
            verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any()) }
        }

        @Test
        fun `processGodkjentInnsoking - oppdaterer deltaker status via beforeUpsert`() = runTest {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()

            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns true
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs

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
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

            // Assert
            val updated = beforeUpsertSlot.captured(deltakerInTest)
            updated.status.type shouldBe DeltakerStatus.Type.DELTAR
            verify { vedtakService.godkjentOkonomiFattVedtak(deltakerInTest) }
        }

        @Test
        fun `processGodkjentInnsoking - skipper videre prosessering naar prisinfo ikke kan godkjennes`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns false
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
            } answers {
                beforeUpsertSlot.captured(firstArg<Deltaker>())
            }

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

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
            verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any()) }
            verify(exactly = 0) { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) }
        }

        @Test
        fun `processGodkjentInnsoking - beforeUpsert kaster exception ved historisk prisinfo`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns false

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
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

            // Assert
            val exception = shouldThrow<IllegalStateException> {
                beforeUpsertSlot.captured(deltakerInTest)
            }
            exception.message shouldBe
                "Fant ikke aktiv ENDRING-prisinfo $totrinnskontrollId for deltaker ${deltakerInTest.id}"
        }

        @Test
        fun `processGodkjentInnsoking - afterUpsert produserer hendelse`() = runTest {
            // Arrange
            val sistEndretAv = UUID.randomUUID()
            val sistEndretAvEnhet = UUID.randomUUID()
            val idag = LocalDate.now()
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
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
                vedtaksinformasjon = vedtaksinformasjon,
            )
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns true
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs

            val afterUpsertSlot = slot<(Deltaker) -> Unit>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = any(),
                    afterUpsert = capture(afterUpsertSlot),
                )
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

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
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
                vedtaksinformasjon = null,
            )
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()

            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns true
            every { vedtakService.godkjentOkonomiFattVedtak(any()) } just Runs
            every { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) } just Runs

            val afterUpsertSlot = slot<(Deltaker) -> Unit>()
            every {
                deltakerService.upsertAndProduceDeltaker(
                    deltaker = any(),
                    erDeltakerSluttdatoEndret = any(),
                    forceProduce = any(),
                    nesteStatus = any(),
                    beforeUpsert = any(),
                    afterUpsert = capture(afterUpsertSlot),
                )
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

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
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )
            val dbError = RuntimeException("Database error")
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()

            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } throws dbError

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
            } returns deltakerInTest

            // Act
            consumer.processGodkjentInnsoking(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

            // Assert - godkjennOkonomi feiler når beforeUpsert kjøres
            shouldThrow<RuntimeException> {
                beforeUpsertSlot.captured(deltakerInTest)
            }
        }
    }

    @Nested
    inner class ProcessGodkjentPrisEndringTest {
        @Test
        fun `kaller godkjennOkonomi med gjennomforingId`() = runTest {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(id = gjennomforingId),
            )
            val navAnsatt = NavAnsatt(
                id = UUID.randomUUID(),
                navIdent = "Z999999",
                navn = "Saksbehandler",
                epost = null,
                telefon = null,
                navEnhetId = UUID.randomUUID(),
            )
            val navEnhet = NavEnhet(
                id = navAnsatt.navEnhetId!!,
                enhetsnummer = "1234",
                navn = "NAV Test",
            )

            every {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = any(),
                    prisinformasjonId = any(),
                )
            } returns true
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                )
            } returns PrisinformasjonDto.IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                )
            } returns PrisinformasjonDto.IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )
            every { distribuerEndringService.produceHendelse(any(), any(), any(), any()) } just Runs

            // Act
            consumer.processGodkjentPrisEndring(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

            // Assert
            verify {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = gjennomforingId,
                    prisinformasjonId = totrinnskontrollId,
                )
            }
        }

        @Test
        fun `processGodkjentPrisEndring - kaster unntak når godkjennOkonomi feiler`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker()
            val exception = RuntimeException("Database error")
            val navAnsatt = NavAnsatt(
                id = UUID.randomUUID(),
                navIdent = "Z123456",
                navn = "Saksbehandler",
                epost = null,
                telefon = null,
                navEnhetId = UUID.randomUUID(),
            )
            val navEnhet = NavEnhet(
                id = navAnsatt.navEnhetId!!,
                enhetsnummer = "1234",
                navn = "NAV Test",
            )

            every {
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = any(),
                    prisinformasjonId = any(),
                )
            } throws exception
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                )
            } returns PrisinformasjonDto.IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                )
            } returns PrisinformasjonDto.IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = null,
            )

            // Act & Assert
            shouldThrow<RuntimeException> {
                consumer.processGodkjentPrisEndring(
                    deltaker = deltakerInTest,
                    prisinfoId = totrinnskontrollId,
                    besluttetAvNavAnsatt = navAnsatt,
                    besluttetAvNavEnhet = navEnhet,
                )
            }
        }

        @Test
        fun `processGodkjentPrisEndring - publiserer ikke når godkjennOkonomi returnerer false`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker()
            val navEnhetId = UUID.randomUUID()
            val navAnsatt = NavAnsatt(
                id = UUID.randomUUID(),
                navIdent = "Z123456",
                navn = "Saksbehandler",
                epost = null,
                telefon = null,
                navEnhetId = navEnhetId,
            )
            val navEnhet = NavEnhet(
                id = navEnhetId,
                enhetsnummer = "1234",
                navn = "NAV Test",
            )
            every { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) } returns false

            // Act
            consumer.processGodkjentPrisEndring(
                deltaker = deltakerInTest,
                prisinfoId = totrinnskontrollId,
                besluttetAvNavAnsatt = navAnsatt,
                besluttetAvNavEnhet = navEnhet,
            )

            // Assert
            verify { PrisinfoRepoAdapter.godkjennOkonomi(any(), any()) }
            verify(exactly = 0) { PrisinfoRepoAdapter.hentPrisinfo(any<UUID>(), PrisinfoDbo.Rolle.GJELDENDE) }
            verify(exactly = 0) { distribuerEndringService.produceHendelse(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class NyDeltakerStatusTest {
        @Test
        fun `nyDeltakerStatus - returnerer FULLFORT når sluttdato er i fortiden`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag.minusWeeks(2),
                sluttdato = idag.minusDays(1),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.FULLFORT
        }

        @Test
        fun `nyDeltakerStatus - returnerer VENTER_PA_OPPSTART når startdato er i fremtiden`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag.plusDays(5),
                sluttdato = idag.plusWeeks(4),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når startdato er idag`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag,
                sluttdato = idag.plusWeeks(4),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når startdato er i fortiden og sluttdato er i fremtiden`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag.minusDays(3),
                sluttdato = idag.plusWeeks(2),
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - returnerer DELTAR når sluttdato er idag`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag.minusDays(5),
                sluttdato = idag,
            )

            // Act
            val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

            // Assert
            status shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `nyDeltakerStatus - kaster feil når startdato mangler`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = null,
                sluttdato = idag.plusWeeks(4),
            )

            // Act & Assert
            shouldThrow<IllegalStateException> {
                TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
            }
        }

        @Test
        fun `nyDeltakerStatus - kaster feil når sluttdato mangler`() {
            // Arrange
            val idag = LocalDate.now()
            val deltakerInTest = lagDeltaker(
                startdato = idag,
                sluttdato = null,
            )

            // Act & Assert
            shouldThrow<IllegalStateException> {
                TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
            }
        }
    }

    companion object {
        private fun lagNavAnsatt(
            navIdent: String = "Z654321",
            navEnhetId: UUID = UUID.randomUUID(),
        ) = lagNavAnsatt(
            id = UUID.randomUUID(),
            navIdent = navIdent,
            navn = "Saksbehandler",
            navEnhetId = navEnhetId,
        )

        private fun lagNavEnhet(id: UUID = UUID.randomUUID()) = lagNavEnhet(
            id = id,
            enhetsnummer = "1234",
            navn = "NAV Test",
        )

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

        private fun avvistEnkeltplassPrisinformasjonPayload(gjennomforingId: UUID): String =
            """
            {
              "id": "${UUID.randomUUID()}",
              "entityId": "$gjennomforingId",
              "type": "ENKELTPLASS_PRISENDRING",
              "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z123456" },
              "behandletTidspunkt": "2026-06-01T10:00:00Z",
              "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z654321" },
              "besluttetTidspunkt": "2026-06-01T10:01:00Z",
              "status": "RETURNERT",
              "aarsaker": ["MANGLER_DOKUMENTASJON"],
              "forklaring": "Ikke godkjent"
            }
            """.trimIndent()

        private fun godkjentEnkeltplassPrisinformasjonPayload(
            gjennomforingId: UUID,
            totrinnskontrollId: UUID,
        ): String =
            """
            {
              "id": "$totrinnskontrollId",
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
