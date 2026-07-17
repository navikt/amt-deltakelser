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
        every { PrisinfoRepository.hentPrisinfo(any(), any()) } answers {
            PrisinfoDbo(
                id = totrinnsIdInTest,
                gjennomforingId = firstArg(),
                okonomiGodkjent = secondArg(),
                prisinfoJsonSubtype = "Anskaffelse",
            )
        }
        every { PrisinfoRepository.hentPrisinfos(any()) } returns emptyList()

        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.hentPrisinfo(any()) } returns Anskaffelse(1000)
    }

    @Nested
    inner class ProduceUpsertGjennomforingTests {
        private fun Deltaker.toPayload(): GjennomforingRequestPayload.UpsertEnkeltplass = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(1000),
            organisasjonsnummer = this.deltakerliste.arrangor!!.organisasjonsnummer,
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
            val deltaker = createUtkastDeltaker()
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.produceUpsertGjennomforing(
                deltaker = deltaker,
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
            val deltaker = createSoktInnDeltaker()
            val payload = deltaker.toPayload()
            val slot = slot<GjennomforingRequestPayload>()
            every { gjennomforingRequestProducer.produce(capture(slot)) } just Runs

            // Act
            sut.produceUpsertGjennomforing(
                deltaker = deltaker,
                endretAvNavIdent = payload.opprettetAv,
                endretAvEnhet = payload.ansvarligEnhet,
            )

            // Assert
            val produced = slot.captured
            produced shouldBe GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = payload,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = totrinnsIdInTest,
                    behandletAv = payload.opprettetAv,
                ),
            )
        }

        @Test
        fun `KLADD status - kaster IllegalStateException`() {
            // Act & Assert
            shouldThrow<IllegalStateException> {
                sut.produceUpsertGjennomforing(
                    deltaker = createKladdDeltaker(),
                    endretAvNavIdent = testPayload.opprettetAv,
                    endretAvEnhet = testPayload.ansvarligEnhet,
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
