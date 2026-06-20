package no.nav.amt.deltaker.bff

import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson3.jackson
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
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureMonitoring
import no.nav.amt.deltaker.bff.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.EnkeltplassClient
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.clients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.DeltakerV2Consumer
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.gjennomforing.GjennomforingConsumer
import no.nav.amt.deltaker.bff.innbygger.NavBrukerConsumer
import no.nav.amt.deltaker.bff.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.bff.innbygger.NavBrukerService
import no.nav.amt.deltaker.bff.job.LeaderElection
import no.nav.amt.deltaker.bff.job.TiltakskoordinatorStengTilgangJob
import no.nav.amt.deltaker.bff.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetConsumer
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.DeltakerEndringHendelseConsumer
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.tiltak.TiltakConsumer
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorConsumer
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.job.JobManager
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.common.audit_log.log.AuditLoggerImpl
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangHttpClient
import kotlin.time.Duration.Companion.seconds
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseBuilder as TiltakskoordinatorResponseBuilder

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
            jackson()
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

    val tiltakskoordinatorClient = TiltakskoordinatorClient(
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

    val arrangorsokClient = ArrangorsokClient(
        baseUrl = environment.mulighetsrommetApiUrl,
        scope = environment.mulighetsrommetApiScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val enkeltplassClient = EnkeltplassClient(
        baseUrl = environment.amtDeltakerUrl,
        scope = environment.amtDeltakerScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val opplaringKategoriseringClient = OpplaringKategoriseringClient(
        baseUrl = environment.mulighetsrommetApiUrl,
        scope = environment.mulighetsrommetApiScope,
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

    val vurderingRepository = VurderingRepository()

    val vurderingService = VurderingService(vurderingRepository)
    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = amtDeltakerClient,
        forslagRepository = forslagRepository,
    )

    val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        paameldingClient = paameldingClient,
    )

    val ulestHendelseRepository = UlestHendelseRepository()
    val ulestHendelseService = UlestHendelseService(ulestHendelseRepository)

    val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
    )
    val selfServiceTilgangService = SelfServiceTilgangService(
        navAnsattService = navAnsattService,
        tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer = tiltakskoordinatorsDeltakerlisteProducer,
    )

    val tiltakskoordinatorTilgangskontrollService = TiltakskoordinatorTilgangskontrollService(
        sporbarhetsloggService = sporbarhetsloggService,
        tilgangskontrollService = tilgangskontrollService,
        deltakerlisteService = deltakerlisteService,
        selfServiceTilgangService = selfServiceTilgangService,
    )

    val tiltakRepository = TiltakRepository()

    val unleashToggle = CommonUnleashToggle(unleash)
    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        GjennomforingConsumer(
            deltakerRepository = deltakerRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            arrangorService = arrangorService,
            tiltakRepository = tiltakRepository,
            pameldingService = pameldingService,
            unleashToggle = unleashToggle,
            selfServiceTilgangService = selfServiceTilgangService,
        ),
        NavAnsattConsumer(navAnsattService),
        NavBrukerConsumer(navBrukerService, pameldingService),
        TiltakConsumer(tiltakRepository),
        DeltakerV2Consumer(
            deltakerRepository,
            deltakerService,
            deltakerlisteRepository,
            vurderingService,
            navBrukerService,
            unleashToggle,
        ),
        ArrangorMeldingConsumer(forslagRepository),
        DeltakerEndringHendelseConsumer(ulestHendelseService, ulestHendelseRepository),
        NavEnhetConsumer(navEnhetService),
    )
    consumers.forEach { it.start() }

    configureAuthentication(environment)
    configureRequestValidation()
    configureRouting(
        tilgangskontrollService = tilgangskontrollService,
        deltakerService = deltakerService,
        pameldingService = pameldingService,
        paameldingClient = paameldingClient,
        navAnsattService = navAnsattService,
        forslagRepository = forslagRepository,
        amtDistribusjonClient = amtDistribusjonClient,
        amtDeltakerClient = amtDeltakerClient,
        arrangorsokClient = arrangorsokClient,
        enkeltplassClient = enkeltplassClient,
        sporbarhetsloggService = sporbarhetsloggService,
        deltakerlisteService = deltakerlisteService,
        deltakerlisteRepository = deltakerlisteRepository,
        unleash = unleash,
        tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
        tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
        ulestHendelseRepository = ulestHendelseRepository,
        selfServiceTilgangService = selfServiceTilgangService,
        opplaringKategoriseringClient = opplaringKategoriseringClient,
        tiltakskoordinatorClient = tiltakskoordinatorClient,
        tiltakskoordinatorResponseBuilder = TiltakskoordinatorResponseBuilder(ulestHendelseRepository),
    )
    configureMonitoring()

    val tiltakskoordinatorStengTilgangJob = TiltakskoordinatorStengTilgangJob(
        leaderElection = leaderElection,
        attributes = attributes,
        selfServiceTilgangService = selfServiceTilgangService,
    )
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
