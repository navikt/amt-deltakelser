package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.repository.PrisinfoGjennomforingLinkRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Test

class TotrinnskontrollConsumerInnsokingIntegrationTest : IntegrationTestWithDbBase() {
    override val vedtakService: VedtakService = mockk(relaxed = true)
    override val distribuerEndringService: DistribuerEndringService = mockk(relaxed = true)
    override val deltakerProducerService: DeltakerProducerService = mockk(relaxed = true)

    private val consumer by lazy {
        TotrinnskontrollConsumer(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            vedtakService = vedtakService,
            distribuerEndringService = distribuerEndringService,
            navAnsattService = navAnsattService,
        )
    }

    @Test
    fun `historisk prisgodkjenning lagres uten deltakeroppdatering vedtak eller hendelse`() {
        // Arrange
        val gjennomforing = lagDeltakerliste()
        val deltaker = lagDeltaker(
            gjennomforing = gjennomforing,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val navEnhet = lagNavEnhet()
        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        TestRepository.insert(deltaker)
        TestRepository.insert(navAnsatt)

        val gjeldendePrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
            gjennomforingId = gjennomforing.id,
            prisinformasjon = Anskaffelse(pris = 10000),
        )
        val gjeldendePrisinfoGodkjent = PrisinfoRepoAdapter.godkjennOkonomi(
            gjennomforingId = gjennomforing.id,
            prisinformasjonId = gjeldendePrisinfoId,
            godkjentAv = navAnsatt.id,
            godkjentAvEnhet = navEnhet.id,
        )

        val historiskPrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
            gjennomforingId = gjennomforing.id,
            prisinformasjon = Anskaffelse(pris = 15000),
            begrunnelse = "Historisk prisendring",
        )
        val aktivPrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
            gjennomforingId = gjennomforing.id,
            prisinformasjon = Anskaffelse(pris = 20000),
            begrunnelse = "Nyere prisendring",
        )
        val deltakerFoer = deltakerRepository.get(deltaker.id).shouldNotBeNull()

        // Act
        consumer.processGodkjentInnsoking(
            deltaker = deltakerFoer,
            prisinfoId = historiskPrisinfoId,
            behandletAvNavAnsatt = navAnsatt,
            behandletAvNavEnhet = navEnhet,
            godkjentAvNavAnsatt = navAnsatt,
            godkjentAvNavEnhet = navEnhet,
        )

        // Assert
        gjeldendePrisinfoGodkjent shouldBe true
        PrisinfoRepository
            .hentPrisinfo(historiskPrisinfoId)
            .shouldNotBeNull()
            .status shouldBe PrisinfoDbo.TotrinnskontrollStatus.GODKJENT

        val registrertGodkjenner = Database.query { session ->
            session.run(
                queryOf(
                    """
                    SELECT godkjent_av, godkjent_av_enhet
                    FROM prisinformasjon_godkjenning
                    WHERE prisinformasjon_id = ?
                    """.trimIndent(),
                    historiskPrisinfoId,
                ).map { row -> row.uuid("godkjent_av") to row.uuid("godkjent_av_enhet") }
                    .asSingle,
            )
        }
        registrertGodkjenner shouldBe (navAnsatt.id to navEnhet.id)

        val deltakerEtter = deltakerRepository.get(deltaker.id).shouldNotBeNull()
        deltakerEtter.status shouldBe deltakerFoer.status
        deltakerEtter.sistEndret shouldBe deltakerFoer.sistEndret
        PrisinfoGjennomforingLinkRepository.hentPrisinformasjonIdForEndring(gjennomforing.id) shouldBe aktivPrisinfoId
        PrisinfoRepository
            .hentPrisinfo(gjennomforing.id, PrisinfoDbo.Rolle.GJELDENDE)
            .shouldNotBeNull()
            .id shouldBe gjeldendePrisinfoId

        verify(exactly = 0) { vedtakService.godkjentOkonomiFattVedtak(any(), any(), any()) }
        verify(exactly = 0) { distribuerEndringService.produceHendelseForUtkast(any(), any(), any(), any()) }
        verify(exactly = 0) { deltakerProducerService.produce(any(), any(), any(), any(), any()) }
    }
}
