package no.nav.amt.deltaker

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
import no.nav.amt.deltaker.Environment.Companion.HTTP_CONNECT_TIMEOUT_MILLIS
import no.nav.amt.deltaker.Environment.Companion.HTTP_REQUEST_TIMEOUT_MILLIS
import no.nav.amt.deltaker.Environment.Companion.HTTP_SOCKET_TIMEOUT_MILLIS
import no.nav.amt.deltaker.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureMonitoring
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.ArrangorConsumer
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.deltaker.ResponseBuilder
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakelsesmengdeUpdateJob
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.EnkeltplassDeltakerConsumer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.StatusUpdateJob
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerConsumer
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetConsumer
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
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

    val amtPersonServiceClient = AmtPersonServiceClient(
        baseUrl = environment.amtPersonServiceUrl,
        scope = environment.amtPersonServiceScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val amtArrangorClient = AmtArrangorClient(
        baseUrl = environment.amtArrangorUrl,
        scope = environment.amtArrangorScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val isOppfolgingsTilfelleClient = IsOppfolgingstilfelleClient(
        baseUrl = environment.isOppfolgingstilfelleUrl,
        scope = environment.isOppfolgingstilfelleScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
    )

    val mulighetsrommetApiClient = MulighetsrommetApiClient(
        baseUrl = environment.mulighetsrommetApiUrl,
        scope = environment.mulighetsrommetApiScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
    )

    val amtDistribusjonClient = AmtDistribusjonClient(
        baseUrl = environment.amtDistribusjonServiceUrl,
        scope = environment.amtDistribusjonServiceScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
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
    val navAnsattRepository = NavAnsattRepository()
    val navEnhetRepository = NavEnhetRepository()
    val navBrukerRepository = NavBrukerRepository()
    val tiltakstypeRepository = TiltakstypeRepository()
    val deltakerlisteRepository = DeltakerlisteRepository()
    val deltakerRepository = DeltakerRepository()
    val deltakerEndringRepository = DeltakerEndringRepository()
    val vedtakRepository = VedtakRepository()
    val forslagRepository = ForslagRepository()
    val endringFraArrangorRepository = EndringFraArrangorRepository()
    val importertFraArenaRepository = ImportertFraArenaRepository()
    val vurderingRepository = VurderingRepository()

    val poaoTilgangCachedClient = PoaoTilgangCachedClient.createDefaultCacheClient(
        PoaoTilgangHttpClient(
            baseUrl = environment.poaoTilgangUrl,
            tokenProvider = { runBlocking { azureAdTokenClient.getMachineToMachineTokenWithoutType(environment.poaoTilgangScope) } },
        ),
    )

    val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)
    val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient, navEnhetService)
    val navBrukerService = NavBrukerService(
        navBrukerRepository,
        amtPersonServiceClient,
        navEnhetService,
        navAnsattService,
    )
    val vurderingService = VurderingService(vurderingRepository)
    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)
    val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
    val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
    val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        vedtakRepository,
        forslagRepository,
        endringFraArrangorRepository,
        importertFraArenaRepository,
        innsokPaaFellesOppstartRepository,
        endringFraTiltakskoordinatorRepository,
        vurderingRepository,
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
    val unleashToggle = CommonUnleashToggle(unleash)

    val hendelseProducer = HendelseProducer(outboxService)
    val hendelseService = HendelseService(
        hendelseProducer = hendelseProducer,
        navAnsattRepository = navAnsattRepository,
        navAnsattService = navAnsattService,
        navEnhetRepository = navEnhetRepository,
        navEnhetService = navEnhetService,
        arrangorService = arrangorService,
        deltakerHistorikkService = deltakerHistorikkService,
        vurderingService = vurderingService,
        unleashToggle = unleashToggle,
    )

    val deltakerKafkaPayloadBuilder =
        DeltakerKafkaPayloadBuilder(navAnsattRepository, navEnhetRepository, deltakerHistorikkService, vurderingRepository)

    val deltakerProducer = DeltakerProducer(
        outboxService = outboxService,
        producer = kafkaProducer,
    )
    val deltakerV1Producer = DeltakerV1Producer(
        outboxService = outboxService,
        producer = kafkaProducer,
    )

    val deltakerEksternV1Producer = DeltakerEksternV1Producer(
        outboxService = outboxService,
        producer = kafkaProducer,
    )

    val deltakerProducerService =
        DeltakerProducerService(deltakerKafkaPayloadBuilder, deltakerProducer, deltakerV1Producer, deltakerEksternV1Producer, unleashToggle)

    val forslagService =
        ForslagService(
            forslagRepository = forslagRepository,
            arrangorMeldingProducer = ArrangorMeldingProducer(outboxService),
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
        )

    val deltakerEndringService =
        DeltakerEndringService(
            deltakerEndringRepository,
            navAnsattRepository,
            navEnhetRepository,
            hendelseService,
            forslagService,
            deltakerHistorikkService,
        )

    val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    val vedtakService = VedtakService(vedtakRepository)

    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerProducerService = deltakerProducerService,
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

    val endringFraArrangorService = EndringFraArrangorService(
        deltakerRepository,
        deltakerService,
        endringFraArrangorRepository,
        hendelseService,
        deltakerHistorikkService,
    )

    val opprettKladdRequestValidator = OpprettKladdRequestValidator(
        deltakerlisteRepository = deltakerlisteRepository,
        brukerService = navBrukerService,
        personServiceClient = amtPersonServiceClient,
        isOppfolgingsTilfelleClient = isOppfolgingsTilfelleClient,
    )

    val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        deltakerListeRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
    )

    val responseBuilder = ResponseBuilder(
        arrangorService = arrangorService,
        navAnsattRepository = navAnsattRepository,
        navEnhetService = navEnhetService,
        amtDistribusjonClient = amtDistribusjonClient,
        deltakerHistorikkService = deltakerHistorikkService,
        forslagRepository = forslagRepository,
    )

    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        NavAnsattConsumer(navAnsattRepository, navAnsattService),
        NavBrukerConsumer(navBrukerRepository, navEnhetService, deltakerService),
        TiltakstypeConsumer(tiltakstypeRepository),
        DeltakerlisteConsumer(
            deltakerlisteRepository = deltakerlisteRepository,
            deltakerRepository = deltakerRepository,
            tiltakstypeRepository = tiltakstypeRepository,
            arrangorService = arrangorService,
            deltakerService = deltakerService,
            unleashToggle = unleashToggle,
        ),
        EnkeltplassDeltakerConsumer(
            deltakerRepository,
            deltakerService,
            deltakerlisteRepository,
            navBrukerService,
            importertFraArenaRepository,
            unleashToggle,
            mulighetsrommetApiClient,
            arrangorService,
            tiltakstypeRepository,
            deltakerProducerService,
        ),
        ArrangorMeldingConsumer(
            endringFraArrangorService,
            forslagRepository,
            forslagService,
            deltakerRepository,
            vurderingRepository,
            deltakerProducerService,
        ),
        NavEnhetConsumer(navEnhetRepository),
    )
    consumers.forEach { it.start() }

    configureAuthentication(environment)

    configureRequestValidation(
        opprettKladdRequestValidator = opprettKladdRequestValidator,
    )

    configureRouting(
        pameldingService = pameldingService,
        deltakerService = deltakerService,
        deltakerRepository = deltakerRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        tilgangskontrollService = tilgangskontrollService,
        deltakelserResponseMapper = deltakelserResponseMapper,
        deltakerProducerService = deltakerProducerService,
        vedtakService = vedtakService,
        unleashToggle = unleashToggle,
        innsokPaaFellesOppstartRepository = innsokPaaFellesOppstartRepository,
        vurderingRepository = vurderingRepository,
        hendelseService = hendelseService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navEnhetService = navEnhetService,
        navAnsattService = navAnsattService,
        vedtakRepository = vedtakRepository,
        responseBuilder = responseBuilder,
    )
    configureMonitoring()

    val statusUpdateJob = StatusUpdateJob(leaderElection, attributes, deltakerService)
    statusUpdateJob.startJob()

    val deltakelsesmengdeUpdateJob = DeltakelsesmengdeUpdateJob(
        leaderElection,
        attributes,
        deltakerEndringRepository,
        deltakerEndringService,
        deltakerRepository,
        deltakerService,
    )
    deltakelsesmengdeUpdateJob.startJob()

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
