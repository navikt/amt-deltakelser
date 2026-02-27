package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.TestOutboxEnvironment
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime

class EndringFraArrangorServiceTest {
    private val amtPersonClientMock = mockPersonServiceClient()

    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonClientMock)

    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonClientMock, navEnhetService)

    private val deltakerRepository = DeltakerRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val vedtakRepository = VedtakRepository()
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val vurderingRepository = VurderingRepository()
    private val vurderingService = VurderingService(vurderingRepository)
    private val deltakerHistorikkService =
        DeltakerHistorikkService(
            deltakerEndringRepository,
            vedtakRepository,
            forslagRepository,
            endringFraArrangorRepository,
            importertFraArenaRepository,
            InnsokPaaFellesOppstartRepository(),
            EndringFraTiltakskoordinatorRepository(),
            vurderingRepository,
        )

    private val unleashToggle = mockk<CommonUnleashToggle>(relaxed = true)

    private val hendelseService = HendelseService(
        HendelseProducer(TestOutboxEnvironment.outboxService),
        navAnsattRepository,
        navAnsattService,
        navEnhetRepository,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
        vurderingService,
        unleashToggle,
    )

    private val deltakerKafkaPayloadBuilder = DeltakerKafkaPayloadBuilder(
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        deltakerHistorikkService,
        vurderingRepository,
    )

    private val deltakerProducer = DeltakerProducer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerV1Producer = DeltakerV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerEksternV1Producer =
        DeltakerEksternV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)

    private val deltakerProducerService = DeltakerProducerService(
        deltakerKafkaPayloadBuilder,
        deltakerProducer,
        deltakerV1Producer,
        deltakerEksternV1Producer,
        unleashToggle,
    )

    private val forslagService = ForslagService(
        forslagRepository,
        ArrangorMeldingProducer(TestOutboxEnvironment.outboxService),
        deltakerRepository,
        deltakerProducerService,
    )
    private val vedtakService = VedtakService(vedtakRepository)
    private val deltakerEndringService =
        DeltakerEndringService(
            deltakerEndringRepository,
            navAnsattRepository,
            navEnhetRepository,
            hendelseService,
            forslagService,
            deltakerHistorikkService,
        )

    private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerProducerService = deltakerProducerService,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        vedtakRepository = vedtakRepository,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        forslagRepository = forslagRepository,
    )

    private val endringFraArrangorService = EndringFraArrangorService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        hendelseService = hendelseService,
        deltakerHistorikkService = deltakerHistorikkService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, dato ikke passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().plusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, dato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato uten sluttdato, dato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusDays(2)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = null,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe null
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato uten sluttdato - fjerner ikke eksisterende sluttdato`() = runTest {
        val gammelsluttdato = LocalDate.now().plusDays(2)
        val deltaker = lagDeltaker(
            startdato = LocalDate.of(2021, 1, 1),
            sluttdato = gammelsluttdato,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.of(2021, 1, 2)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = null,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe gammelsluttdato
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
        endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
        (endring.endring as EndringFraArrangor.LeggTilOppstartsdato).sluttdato shouldBe null

        endring.endring shouldBe endringFraArrangor.endring

        val deltakerEtterEndring = deltakerRepository.get(deltaker.id).getOrThrow()

        deltakerEtterEndring.sluttdato shouldBe gammelsluttdato
        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, start- og sluttdato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusMonths(2)
        val sluttdato = LocalDate.now().minusDays(5)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }
}
