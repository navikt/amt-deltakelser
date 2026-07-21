package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import no.nav.amt.deltaker.enkeltplass.GjennomforingUpserter.Companion.toMulighetsrommetKategorisering
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class GjennomforingUpserterTest {
    private val navEnhetRepository = mockk<NavEnhetRepository>()
    private val navAnsattRepository = mockk<NavAnsattRepository>()
    private val vedtakService = mockk<VedtakService>()
    private val gjennomforingRequestProducer = mockk<GjennomforingRequestProducer>()

    private val sut = GjennomforingUpserter(
        navEnhetRepository = navEnhetRepository,
        navAnsattRepository = navAnsattRepository,
        vedtakService = vedtakService,
        gjennomforingRequestProducer = gjennomforingRequestProducer,
    )

    @BeforeEach
    fun setup() = setupDatabaseMocks()

    @AfterEach
    fun tearDown() {
        unmockkObject(Database)
        unmockkObject(PrisinfoRepository)
        unmockkObject(PrisinfoRepoAdapter)
        unmockkObject(OpplaringKategoriseringRepoAdapter)
    }

    private fun setupDatabaseMocks() {
        mockkObject(Database)

        mockkObject(OpplaringKategoriseringRepoAdapter)
        every {
            OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(any())
        } returns OpplaringKategoriseringValg(
            valgteKategoriseringer = emptySet(),
            valgteSertifiseringer = emptySet(),
        )

        mockkObject(PrisinfoRepository)
        every {
            PrisinfoRepository.hentPrisinfo(
                gjennomforingId = any(),
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )
        } answers {
            PrisinfoDbo(
                id = totrinnsIdInTest,
                gjennomforingId = firstArg(),
                rolle = secondArg(),
                prisinfoJsonSubtype = "Anskaffelse",
            )
        }
        every { PrisinfoRepository.hentPrisinfos(any()) } returns emptyList()

        mockkObject(PrisinfoRepoAdapter)
        every {
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = any(),
                brukEndring = true,
            )
        } returns Anskaffelse(1000)
        every {
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(any(), any())
        } returns totrinnsIdInTest
        every {
            PrisinfoRepoAdapter.lagrePrisinfoEndring(any(), any())
        } returns totrinnsIdInTest
    }

    @Nested
    inner class OppdaterPrisinfoTests {
        @Test
        fun `lagrer ny prisinfo og produserer EnkeltplassEndrePrisinformasjon`() {
            // Arrange
            val deltaker = createUtkastDeltaker()
            val nyPrisinfo = Anskaffelse(pris = 50000)
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.produceEndrePrisinfo(
                prisinfo = nyPrisinfo,
                deltaker = deltaker,
                endretAvNavIdent = "Z123456",
            )

            // Assert
            val produced = slot.captured
            produced shouldBe GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
                gjennomforingId = deltaker.deltakerliste.id,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = totrinnsIdInTest,
                    behandletAv = "Z123456",
                ),
                payload = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000),
            )
        }

        @Test
        fun `kaster IllegalStateException når prisinfo ikke finnes etter lagring`() {
            // Arrange
            val deltaker = createUtkastDeltaker()
            val nyPrisinfo = Anskaffelse(pris = 50000)

            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    brukEndring = true,
                )
            } returns null

            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.produceEndrePrisinfo(
                    prisinfo = nyPrisinfo,
                    deltaker = deltaker,
                    endretAvNavIdent = "Z123456",
                )
            }
        }

        @Test
        fun `kalles med ulik prisinformasjon og endretAvNavIdent`() {
            // Arrange
            val deltaker = createSoktInnDeltaker()
            val nyPrisinfo = Anskaffelse(pris = 75000)
            val endretAv = "Z999999"
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.produceEndrePrisinfo(
                prisinfo = nyPrisinfo,
                deltaker = deltaker,
                endretAvNavIdent = endretAv,
            )

            // Assert
            val produced = slot.captured
            (produced as GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon).totrinnskontroll.behandletAv shouldBe endretAv
            produced.gjennomforingId shouldBe deltaker.deltakerliste.id
        }
    }

    @Nested
    inner class ProduceUpsertGjennomforingTests {
        private fun Deltaker.toPayload(): GjennomforingRequestPayload.UpsertEnkeltplass = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000),
            organisasjonsnummer = this.deltakerliste.arrangor!!.organisasjonsnummer,
            ansvarligEnhet = "1234",
            opprettetAv = "Z123456",
            kategorisering = OpplaringKategoriseringRepoAdapter
                .hentOpplaringKategoriseringValg(deltakerliste.id)
                .toMulighetsrommetKategorisering(),
        )

        @Test
        fun `UTKAST_TIL_PAMELDING status - produserer EnkeltplassUtkast`() {
            // Arrange
            val deltaker = createUtkastDeltaker()
            val navIdent = "Z123456"
            val enhet = "1234"
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.publiserGjennomforingUpsert(
                deltaker = deltaker,
                endretAvNavIdent = navIdent,
                endretAvEnhet = enhet,
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
            val deltaker = createSoktInnDeltaker()
            val navIdent = "Z123456"
            val enhet = "1234"
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.publiserGjennomforingUpsert(
                deltaker = deltaker,
                endretAvNavIdent = navIdent,
                endretAvEnhet = enhet,
            )

            // Assert
            val produced = slot.captured
            produced shouldBe GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = payload,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = totrinnsIdInTest,
                    behandletAv = navIdent,
                ),
            )
        }

        @Test
        fun `KLADD status - kaster IllegalStateException`() {
            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.publiserGjennomforingUpsert(
                    deltaker = createKladdDeltaker(),
                    endretAvNavIdent = "Z123456",
                    endretAvEnhet = "1234",
                )
            }
        }
    }

    @Nested
    inner class BuildUpsertPayloadTests {
        @Test
        fun `builds payload with correct values`() {
            // Arrange
            val deltaker = createUtkastDeltaker()
            val navIdent = "Z999999"
            val enhet = "4567"

            // Act
            val payload = sut.buildUpsertPayload(
                deltaker = deltaker,
                opprettetAvNavIdent = navIdent,
                ansvarligEnhet = enhet,
            )

            // Assert
            payload.tiltakskode shouldBe deltaker.deltakerliste.tiltakstype.tiltakskode
            payload.organisasjonsnummer shouldBe deltaker.deltakerliste.arrangor?.organisasjonsnummer
            payload.ansvarligEnhet shouldBe enhet
            payload.opprettetAv shouldBe navIdent
            payload.prisinformasjon shouldBe GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000)
        }

        @Test
        fun `kaster IllegalStateException når prisinfo mangler`() {
            // Arrange
            val deltaker = createUtkastDeltaker()
            every {
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = any(),
                    brukEndring = true,
                )
            } returns null

            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.buildUpsertPayload(
                    deltaker = deltaker,
                    opprettetAvNavIdent = "Z123456",
                    ansvarligEnhet = "1234",
                )
            }
        }

        @Test
        fun `kaster error når organisasjonsnummer er null`() {
            // Arrange
            val deltaker = createUtkastDeltaker().copy(
                deltakerliste = createUtkastDeltaker().deltakerliste.copy(
                    arrangor = null,
                ),
            )

            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.buildUpsertPayload(
                    deltaker = deltaker,
                    opprettetAvNavIdent = "Z123456",
                    ansvarligEnhet = "1234",
                )
            }
        }

        @Test
        fun `inkluderer kategorisering fra repository`() {
            // Arrange
            val deltaker = createUtkastDeltaker()

            // Act
            val payload = sut.buildUpsertPayload(
                deltaker = deltaker,
                opprettetAvNavIdent = "Z123456",
                ansvarligEnhet = "1234",
            )

            // Assert
            payload.kategorisering shouldBe GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                verdier = emptyMap(),
                sertifiseringer = emptySet(),
            )
        }
    }

    @Nested
    inner class BuildGjennomforingRequestTests {
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
        fun `UTKAST_TIL_PAMELDING - returnerer EnkeltplassUtkast`() {
            // Arrange
            val deltaker = createUtkastDeltaker()

            // Act
            val request = sut.buildGjennomforingRequest(
                deltaker = deltaker,
                upsertPayload = testPayload,
                behandletAv = "Z999999",
            )

            // Assert
            request shouldBe GjennomforingRequestPayload.EnkeltplassUtkast(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = testPayload,
            )
        }

        @Test
        fun `SOKT_INN - returnerer EnkeltplassSoktInn med totrinnskontroll`() {
            // Arrange
            val deltaker = createSoktInnDeltaker()
            val behandletAv = "Z999999"

            // Act
            val request = sut.buildGjennomforingRequest(
                deltaker = deltaker,
                upsertPayload = testPayload,
                behandletAv = behandletAv,
            )

            // Assert
            request shouldBe GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = testPayload,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = totrinnsIdInTest,
                    behandletAv = behandletAv,
                ),
            )
        }

        @Test
        fun `SOKT_INN - kaster error når prisinfo mangler`() {
            // Arrange
            val deltaker = createSoktInnDeltaker()
            every {
                PrisinfoRepository.hentPrisinfo(
                    gjennomforingId = any(),
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                )
            } returns null

            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.buildGjennomforingRequest(
                    deltaker = deltaker,
                    upsertPayload = testPayload,
                    behandletAv = "Z999999",
                )
            }
        }

        @Test
        fun `KLADD status - kaster IllegalStateException`() {
            // Arrange
            val deltaker = createKladdDeltaker()

            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.buildGjennomforingRequest(
                    deltaker = deltaker,
                    upsertPayload = testPayload,
                    behandletAv = "Z999999",
                )
            }
        }
    }

    companion object {
        val totrinnsIdInTest: UUID = UUID.randomUUID()
        private val navEnhetInTest = lagNavEnhet(enhetsnummer = "1234")
        private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

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
