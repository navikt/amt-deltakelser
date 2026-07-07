@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService.Companion.toMulighetsrommetKategorisering
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.SertifiseringValgRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.Database.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class EnkeltplassServiceTest : IntegrationTestBase() {
    override val deltakerService = mockk<DeltakerService>()
    override val vedtakService = mockk<VedtakService>()
    override val navEnhetService = mockk<NavEnhetService>()
    override val navAnsattService = mockk<NavAnsattService>()
    override val deltakerProducerService = mockk<DeltakerProducerService>()
    override val distribuerEndringService = mockk<DistribuerEndringService>(relaxed = true)

    @BeforeEach
    fun setup() {
        setupDatabaseMocks()
        setupRepositoryMocks()
        setupNavEnhetOgAnsattMocks()
        setupOpplaringKategoriseringClientMocks()
        stubDefaultDeltakere()
        every {
            deltakerProducerService.produce(
                deltaker = any(),
                forcedUpdate = any(),
                publiserTilDeltakerV1 = any(),
                publiserTilDeltakerEksternV1 = any(),
                publiserTilDeltakerV2 = any(),
            )
        } just Runs
    }

    private fun setupRepositoryMocks() {
        every { deltakerlisteRepository.update(any()) } just Runs
        every { deltakerRepository.updateEnkeltplass(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SertifiseringValgRepository)
        unmockkObject(OpplaringKategoriseringRepoAdapter)
        unmockkObject(PrisinfoRepoAdapter)
    }

    private fun stubDefaultDeltakere() {
        stubDeltaker(kladdDeltakerInTest)
        stubDeltaker(utkastDeltakerInTest)
        stubDeltaker(soktInnDeltakerInTest)
    }

    private fun stubDeltaker(deltaker: Deltaker) {
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
    }

    private fun setupDatabaseMocks() {
        // Note: MockK cleanup is handled by IntegrationTestBase.init() which calls
        // clearAllMocks() before each test via @BeforeEach, preventing mock leakage
        // between tests. No explicit unmockkObject() calls are needed.
        mockkObject(Database)
        every { transaction<Any>(any()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }

        mockkObject(SertifiseringValgRepository)
        every { SertifiseringValgRepository.deleteForGjennomforing(any()) } just Runs
        every { SertifiseringValgRepository.lagreSertifiseringValg(any(), any()) } just Runs
        every { SertifiseringValgRepository.hentSertifiseringValg(any()) } returns emptySet()

        mockkObject(OpplaringKategoriseringRepoAdapter)
        every {
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = any(),
                valgteVerdier = any(),
                valgteSertifiseringer = any(),
            )
        } just Runs

        every {
            OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(any())
        } returns OpplaringKategoriseringValg(
            valgteKategoriseringer = emptySet(),
            valgteSertifiseringer = emptySet(),
        )

        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.lagrePrisinfo(any(), any()) } just Runs
        every { PrisinfoRepoAdapter.hentPrisinfo(any()) } returns PrisinformasjonDto.Anskaffelse(1000)
    }

    private fun setupNavEnhetOgAnsattMocks() {
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(navEnhetInTest.enhetsnummer) } returns navEnhetInTest
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsattInTest.navIdent) } returns navAnsattInTest
    }

    private fun setupOpplaringKategoriseringClientMocks() {
        coEvery { opplaringKategoriseringClient.hentOpplaringKategorisering(any()) } returns OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = emptyList(),
        )
    }

    @Nested
    inner class OppdaterKladdTests {
        private val oppdaterKladdRequest = OppdaterEnkeltplassKladdRequest(
            beskrivelse = null,
            prisinformasjon = null,
            startdato = null,
            sluttdato = null,
            arrangorUnderenhet = null,
        )

        @Test
        fun `skal kaste exception for gjennomforing som ikke er enkeltplass`() = runTest {
            // Arrange
            val deltaker = kladdDeltakerInTest.copy(
                deltakerliste = kladdDeltakerInTest.deltakerliste.copy(gjennomforingstype = GjennomforingType.Gruppe),
            )
            stubDeltaker(deltaker)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    oppdaterKladdRequest = oppdaterKladdRequest,
                )
            }
        }

        @Test
        fun `skal kaste exception for deltaker som ikke er i KLADD status`() = runTest {
            // Arrange
            stubDeltaker(utkastDeltakerInTest)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = utkastDeltakerInTest.id,
                    oppdaterKladdRequest = oppdaterKladdRequest,
                )
            }
        }

        @Test
        fun `sertifiseringValg med verdier - sletter eksisterende og lagrer nye`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
            )
            val request = oppdaterKladdRequest.copy(sertifiseringValg = sertifiseringer)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify {
                OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                    gjennomforingId = kladdDeltakerInTest.deltakerliste.id,
                    valgteVerdier = null,
                    valgteSertifiseringer = sertifiseringer,
                )
            }
        }

        @Test
        fun `sertifiseringValg tomt sett - sletter eksisterende uten aa lagre nye`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val request = oppdaterKladdRequest.copy(sertifiseringValg = emptySet())

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify {
                OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                    gjennomforingId = kladdDeltakerInTest.deltakerliste.id,
                    valgteVerdier = null,
                    valgteSertifiseringer = emptySet(),
                )
            }
        }

        @Test
        fun `sertifiseringValg null - roerer ikke sertifiseringer`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val request = oppdaterKladdRequest.copy(sertifiseringValg = null)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify(exactly = 0) { SertifiseringValgRepository.deleteForGjennomforing(any()) }
            verify(exactly = 0) { SertifiseringValgRepository.lagreSertifiseringValg(any(), any()) }
        }

        @Test
        fun `skal ikke lagre prisinformasjon naar den er null`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val request = oppdaterKladdRequest.copy(prisinformasjon = null)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify(exactly = 0) { PrisinfoRepoAdapter.lagrePrisinfo(any(), any()) }
        }

        @Test
        fun `skal oppdatere prisinformasjon naar gitt`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val nyPrisinformasjon = PrisinformasjonDto.Anskaffelse(42)
            val request = oppdaterKladdRequest.copy(prisinformasjon = nyPrisinformasjon)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify {
                deltakerlisteRepository.update(any())
                PrisinfoRepoAdapter.lagrePrisinfo(
                    gjennomforingId = kladdDeltakerInTest.deltakerliste.id,
                    prisinformasjon = nyPrisinformasjon,
                )
            }
        }

        @Test
        fun `skal oppdatere dato-felter naar gitt`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs

            val startDato = LocalDate.now().plusDays(10)
            val sluttDato = LocalDate.now().plusMonths(3)
            val request = oppdaterKladdRequest.copy(startdato = startDato, sluttdato = sluttDato)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = kladdDeltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify { deltakerRepository.updateEnkeltplass(any()) }
        }
    }

    @Nested
    inner class DelUtkastMedInnbyggerTests {
        // Complex tests that require database access are in EnkeltplassServiceIntegrationTest

        @Test
        fun `skal kaste exception for gjennomforing som ikke er enkeltplass`() = runTest {
            // Arrange
            val deltaker = kladdDeltakerInTest.copy(
                deltakerliste = kladdDeltakerInTest.deltakerliste.copy(
                    gjennomforingstype = GjennomforingType.Gruppe,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            stubDeltaker(deltaker)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.delUtkastMedInnbygger(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }

        @Test
        fun `skal kaste exception for deltaker som ikke er i UTKAST status`() = runTest {
            // Arrange
            stubDeltaker(soktInnDeltakerInTest)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.delUtkastMedInnbygger(
                    deltakerId = soktInnDeltakerInTest.id,
                    decoratedRequest = decoratedRequest,
                )
            }
        }
    }

    @Nested
    inner class OppdaterUtkastTests {
        @Test
        fun `skal oppdatere deltaker og vedtak uten aa endre status`() = runTest {
            // Arrange
            every {
                deltakerRepository.get(kladdDeltakerInTest.id)
            } returns Result.success(
                kladdDeltakerInTest.copy(
                    status = kladdDeltakerInTest.status.copy(
                        type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
                    ),
                ),
            )

            every { arrangorRepository.get(any<String>()) } returns arrangorInTest
            every { deltakerRepository.updateEnkeltplass(any()) } just Runs
            every { deltakerlisteRepository.update(any()) } just Runs

            // Act
            enkeltplassService.oppdaterUtkast(
                deltakerId = kladdDeltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
            verify(exactly = 0) {
                deltakerService.lagreDeltakerStatus(any(), any(), any())
            }
            verify { deltakerlisteRepository.update(any()) }
            verify { deltakerRepository.updateEnkeltplass(any()) }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er enkeltplass`() = runTest {
            // Arrange
            val deltaker = kladdDeltakerInTest.copy(
                deltakerliste = kladdDeltakerInTest.deltakerliste.copy(
                    gjennomforingstype = GjennomforingType.Gruppe,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            stubDeltaker(deltaker)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterUtkast(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er i KLADD status`() = runTest {
            // Arrange
            val deltaker = kladdDeltakerInTest.copy(
                deltakerliste = kladdDeltakerInTest.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )
            stubDeltaker(deltaker)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterUtkast(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }
    }

    @Nested
    inner class ProduceUpsertGjennomforingTests {
        private fun Deltaker.toPayload(): GjennomforingRequestPayload.UpsertEnkeltplass = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000),
            organisasjonsnummer = "987654321",
            ansvarligEnhet = "1234",
            opprettetAv = "Z123456",
            kategorisering = deltakerliste.opplaringKategorisering?.toMulighetsrommetKategorisering(),
        )

        private val testPayload = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000),
            organisasjonsnummer = "987654321",
            ansvarligEnhet = "1234",
            opprettetAv = "Z123456",
            kategorisering = GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                verdier = emptyMap(),
                sertifiseringer = emptySet(),
            ),
        )

        @Test
        fun `UTKAST_TIL_PAMELDING status - produserer EnkeltplassUtkast`() {
            // Arrange
            val deltaker = utkastDeltakerInTest
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { outboxService.insertRecord(any(), capture(slot), any(), any()) } returns mockk()

            // Act
            enkeltplassService.produceUpsertGjennomforing(
                deltaker = deltaker,
                orgnr = payload.organisasjonsnummer,
                endretAvNavIdent = payload.opprettetAv,
                endretAvEnhet = payload.ansvarligEnhet,
            )

            // Assert
            val produced = slot.captured
            produced shouldBe GjennomforingRequestPayload.EnkeltplassUtkast(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = payload,
            )
        }

        @Test
        fun `SOKT_INN status - produserer EnkeltplassSoktInn`() {
            // Arrange
            val deltaker = soktInnDeltakerInTest
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { outboxService.insertRecord(any(), capture(slot), any(), any()) } returns mockk()

            // Act
            enkeltplassService.produceUpsertGjennomforing(
                deltaker = deltaker,
                orgnr = payload.organisasjonsnummer,
                endretAvNavIdent = payload.opprettetAv,
                endretAvEnhet = payload.ansvarligEnhet,
            )

            // Assert
            val produced = slot.captured
            produced shouldBe GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = payload,
                totrinnkontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = deltaker.id,
                    behandletAv = payload.opprettetAv,
                ),
            )
        }

        @Test
        fun `KLADD status - kaster IllegalStateException`() {
            // Act & Assert
            shouldThrow<IllegalStateException> {
                enkeltplassService.produceUpsertGjennomforing(
                    deltaker = kladdDeltakerInTest,
                    orgnr = testPayload.organisasjonsnummer,
                    endretAvNavIdent = testPayload.opprettetAv,
                    endretAvEnhet = testPayload.ansvarligEnhet,
                )
            }
        }
    }

    companion object {
        private val navEnhetInTest = lagNavEnhet(enhetsnummer = "1234")
        private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

        private val kladdDeltakerInTest = createKladdDeltaker()
        private val utkastDeltakerInTest = createUtkastDeltaker()
        private val soktInnDeltakerInTest = createSoktInnDeltaker()

        private val pameldingRequestInTest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654322",
            prisinformasjon = PrisinformasjonDto.Anskaffelse(1234),
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = pameldingRequestInTest,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            endretAv = navAnsattInTest.navIdent,
        )

        private val arrangorInTest = Arrangor(
            id = UUID.randomUUID(),
            organisasjonsnummer = pameldingRequestInTest.arrangorUnderenhet,
            navn = "Test Arrangor",
            overordnetArrangorId = null,
        )

        private fun createBaseDeltaker() = lagDeltaker(
            navBruker = lagNavBruker(
                navEnhetId = navEnhetInTest.id,
                navVeilederId = navAnsattInTest.id,
            ),
            deltakerliste = lagDeltakerliste(
                gjennomforingstype = GjennomforingType.Enkeltplass,
                status = GjennomforingStatusType.KLADD,
                prisinformasjon = "1234",
                opplaringKategorisering = TestData.lagOpplaringKategorisering(),
            ),
        )

        fun createKladdDeltaker() = createBaseDeltaker().copy(
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.KLADD),
        )

        fun createUtkastDeltaker() = createBaseDeltaker().copy(
            id = UUID.randomUUID(),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )

        fun createSoktInnDeltaker() = createBaseDeltaker().copy(
            id = UUID.randomUUID(),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.SOKT_INN),
        )
    }
}
