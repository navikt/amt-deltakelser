package no.nav.amt.deltaker.bff

import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.Environment.Companion.HTTP_CONNECT_TIMEOUT_MILLIS
import no.nav.amt.deltaker.bff.Environment.Companion.HTTP_REQUEST_TIMEOUT_MILLIS
import no.nav.amt.deltaker.bff.Environment.Companion.HTTP_SOCKET_TIMEOUT_MILLIS
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.apiclients.paamelding.PaameldingClient
import no.nav.amt.deltaker.bff.apiclients.tiltakskoordinator.TiltaksKoordinatorClient
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureMonitoring
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.arrangor.ArrangorConsumer
import no.nav.amt.deltaker.bff.arrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.arrangor.ArrangorService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorStengTilgangJob
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.bff.deltaker.job.SlettUtdatertKladdJob
import no.nav.amt.deltaker.bff.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.bff.deltaker.kafka.DeltakerV2Consumer
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerConsumer
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.bff.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.bff.innbygger.InnbyggerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetConsumer
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.deltaker.bff.testdata.TestdataService
import no.nav.amt.deltaker.bff.tiltakskoordinator.SporbarhetOgTilgangskontrollSvc
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.kafka.HendelseConsumer
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.job.JobManager
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.common.audit_log.log.AuditLoggerImpl
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangHttpClient
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(
        factory = Netty,
        configure = {
            connector {
                port = 8080
            }
            shutdownGracePeriod = 10.seconds.inWholeMilliseconds
            shutdownTimeout = 20.seconds.inWholeMilliseconds
        },
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    val environment = Environment()

    Database.init(environment.databaseConfig)

    val httpClient = HttpClient(CIO.create()) {
        install(ContentNegotiation) {
            jackson { applicationConfig() }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MILLIS
        }
    }

    val leaderElection = LeaderElection(httpClient, environment.electorPath)

    val azureAdTokenClient = AzureAdTokenClient(
        azureAdTokenUrl = environment.azureAdTokenUrl,
        clientId = environment.azureClientId,
        clientSecret = environment.azureClientSecret,
        httpClient = httpClient,
    )

    val amtArrangorClient = AmtArrangorClient(
        baseUrl = environment.amtArrangorUrl,
        scope = environment.amtArrangorScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val amtPersonServiceClient = AmtPersonServiceClient(
        baseUrl = environment.amtPersonServiceUrl,
        scope = environment.amtPersonServiceScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val amtDeltakerClient = AmtDeltakerClient(
        baseUrl = environment.amtDeltakerUrl,
        scope = environment.amtDeltakerScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val paameldingClient = PaameldingClient(
        baseUrl = environment.amtDeltakerUrl,
        scope = environment.amtDeltakerScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val tiltakskoordinatorClient = TiltaksKoordinatorClient(
        baseUrl = environment.amtDeltakerUrl,
        scope = environment.amtDeltakerScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val amtDistribusjonClient = AmtDistribusjonClient(
        baseUrl = environment.amtDistribusjonUrl,
        scope = environment.amtDistribusjonScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val unleash = DefaultUnleash(
        UnleashConfig
            .builder()
            .appName(environment.appName)
            .instanceId(environment.appName)
            .unleashAPI("${environment.unleashUrl}/api")
            .apiKey(environment.unleashApiToken)
            .build(),
    )

    val kafkaProducer = Producer<String, String>(
        if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
    )

    // START outbox config
    val outboxService = OutboxService()
    val jobManager = JobManager(
        isLeader = leaderElection::isLeader,
        applicationIsReady = { attributes.getOrNull(isReadyKey) == true },
    )
    val outboxProcessor = OutboxProcessor(outboxService, jobManager, kafkaProducer)
    outboxProcessor.start()
    // END outbox config

    val arrangorRepository = ArrangorRepository()
    val deltakerlisteRepository = DeltakerlisteRepository()
    val navAnsattRepository = NavAnsattRepository()
    val navEnhetRepository = NavEnhetRepository()
    val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient)
    val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)

    val navBrukerRepository = NavBrukerRepository()
    val navBrukerService = NavBrukerService(
        amtPersonServiceClient,
        navBrukerRepository,
        navAnsattService,
        navEnhetService,
    )

    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)
    val deltakerlisteService = DeltakerlisteService(deltakerlisteRepository)

    val poaoTilgangCachedClient = PoaoTilgangCachedClient.createDefaultCacheClient(
        PoaoTilgangHttpClient(
            baseUrl = environment.poaoTilgangUrl,
            tokenProvider = { runBlocking { azureAdTokenClient.getMachineToMachineTokenWithoutType(environment.poaoTilgangScope) } },
        ),
    )

    val tiltakskoordinatorTilgangRepository = TiltakskoordinatorTilgangRepository()
    val tiltakskoordinatorsDeltakerlisteProducer = TiltakskoordinatorsDeltakerlisteProducer(
        outboxService,
        kafkaProducer,
    )

    val sporbarhetsloggService = SporbarhetsloggService(AuditLoggerImpl())

    val deltakerRepository = DeltakerRepository()

    val forslagRepository = ForslagRepository()
    val arrangorMeldingProducer = ArrangorMeldingProducer(outboxService)
    val forslagService = ForslagService(forslagRepository, navAnsattService, navEnhetService, arrangorMeldingProducer)

    val vurderingRepository = VurderingRepository()
    val vurderingService = VurderingService(vurderingRepository)
    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = amtDeltakerClient,
        navEnhetService = navEnhetService,
        forslagRepository = forslagRepository,
    )

    val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = navBrukerService,
        navEnhetService = navEnhetService,
        paameldingClient = paameldingClient,
        amtDeltakerClient = amtDeltakerClient,
    )

    val innbyggerService = InnbyggerService(deltakerService, paameldingClient)

    val ulestHendelseRepository = UlestHendelseRepository()
    val ulestHendelseService = UlestHendelseService(ulestHendelseRepository)

    val tiltakskoordinatorService = TiltakskoordinatorService(
        tiltaksKoordinatorClient = tiltakskoordinatorClient,
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        vurderingService = vurderingService,
        navEnhetService = navEnhetService,
        navAnsattService = navAnsattService,
        amtDistribusjonClient = amtDistribusjonClient,
        forslagRepository = forslagRepository,
        ulesteHendelseService = ulestHendelseService,
    )

    val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
        tiltakskoordinatorService,
        deltakerlisteService,
    )

    val sporbarhetOgTilgangskontrollSvc = SporbarhetOgTilgangskontrollSvc(
        sporbarhetsloggService = sporbarhetsloggService,
        tilgangskontrollService,
        deltakerlisteService,
    )

    val tiltakstypeRepository = TiltakstypeRepository()

    val testdataService = TestdataService(
        pameldingService = pameldingService,
        deltakerlisteService = deltakerlisteService,
        arrangorMeldingProducer = arrangorMeldingProducer,
        deltakerRepository = deltakerRepository,
    )

    val unleashToggle = CommonUnleashToggle(unleash)
    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        DeltakerlisteConsumer(
            deltakerRepository = deltakerRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            arrangorService = arrangorService,
            tiltakstypeRepository = tiltakstypeRepository,
            pameldingService = pameldingService,
            tilgangskontrollService = tilgangskontrollService,
            unleashToggle = unleashToggle,
        ),
        NavAnsattConsumer(navAnsattService),
        NavBrukerConsumer(navBrukerService, pameldingService),
        TiltakstypeConsumer(tiltakstypeRepository),
        DeltakerV2Consumer(
            deltakerRepository,
            deltakerService,
            deltakerlisteRepository,
            vurderingService,
            navBrukerService,
            unleashToggle,
        ),
        ArrangorMeldingConsumer(forslagRepository),
        HendelseConsumer(ulestHendelseService),
        NavEnhetConsumer(navEnhetService),
    )
    consumers.forEach { it.start() }

    configureAuthentication(environment)
    configureRouting(
        tilgangskontrollService = tilgangskontrollService,
        deltakerService = deltakerService,
        pameldingService = pameldingService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        innbyggerService = innbyggerService,
        forslagRepository = forslagRepository,
        forslagService = forslagService,
        amtDistribusjonClient = amtDistribusjonClient,
        amtDeltakerClient = amtDeltakerClient,
        sporbarhetsloggService = sporbarhetsloggService,
        deltakerRepository = deltakerRepository,
        deltakerlisteService = deltakerlisteService,
        unleash = unleash,
        sporbarhetOgTilgangskontrollSvc = sporbarhetOgTilgangskontrollSvc,
        tiltakskoordinatorService = tiltakskoordinatorService,
        tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
        ulestHendelseService = ulestHendelseService,
        testdataService = testdataService,
    )
    configureMonitoring()

    val slettUtdatertKladdJob = SlettUtdatertKladdJob(leaderElection, attributes, deltakerRepository, pameldingService)
    slettUtdatertKladdJob.startJob()

    val tiltakskoordinatorStengTilgangJob = TiltakskoordinatorStengTilgangJob(leaderElection, attributes, tilgangskontrollService)
    tiltakskoordinatorStengTilgangJob.startJob()

    attributes.put(isReadyKey, true)

    monitor.subscribe(ApplicationStopPreparing) {
        attributes.put(isReadyKey, false)
        log.info("Shutting down application (ApplicationStopPreparing)")
    }

    monitor.subscribe(ApplicationStopping) {
        runBlocking {
            log.info("Shutting down consumers")
            consumers.forEach {
                runCatching {
                    it.close()
                }.onFailure { throwable ->
                    log.error("Error shutting down consumer", throwable)
                }
            }
        }
    }

    monitor.subscribe(ApplicationStopped) {
        log.info("Shutting down database")
        Database.close()

        log.info("Shutting down producers")
        runCatching {
            kafkaProducer.close()
        }.onFailure { throwable ->
            log.error("Error shutting down producers", throwable)
        }
    }
}
