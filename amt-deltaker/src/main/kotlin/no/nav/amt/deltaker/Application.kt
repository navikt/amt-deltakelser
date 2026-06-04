package no.nav.amt.deltaker

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
import no.nav.amt.deltaker.Environment.Companion.HTTP_CONNECT_TIMEOUT_MILLIS
import no.nav.amt.deltaker.Environment.Companion.HTTP_REQUEST_TIMEOUT_MILLIS
import no.nav.amt.deltaker.Environment.Companion.HTTP_SOCKET_TIMEOUT_MILLIS
import no.nav.amt.deltaker.api.external.response.DeltakelserResponseMapper
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder
import no.nav.amt.deltaker.application.plugins.OpprettKladdRequestValidator
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureMonitoring
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.clients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.enkeltplass.kafka.EnkeltplassDeltakerConsumer
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.innbygger.DistribuerEndringProducer
import no.nav.amt.deltaker.innbygger.NavBrukerConsumer
import no.nav.amt.deltaker.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.job.DeltakelsesmengdeUpdateJob
import no.nav.amt.deltaker.job.StatusUpdateJob
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.kafka.GjennomforingConsumer
import no.nav.amt.deltaker.kafka.payload.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetConsumer
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltak.TiltakConsumer
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorConsumer
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorMeldingConsumer
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorMeldingProducer
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.deltaker.veileder.PameldingService
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
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
            jackson()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MILLIS
        }
    }

    val leaderElection = LeaderElection(
        httpClient = httpClient,
        electorPath = environment.electorPath,
    )

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

    val amtDistribusjonClient = AmtDistribusjonClient(
        baseUrl = environment.amtDistribusjonServiceUrl,
        scope = environment.amtDistribusjonServiceScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
    )

    val kodeverkClient = KodeverkClient(
        baseUrl = environment.mulighetsrommetApiUrl,
        scope = environment.mulighetsrommetApiScope,
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

    val outboxProcessor = OutboxProcessor(
        outboxService = outboxService,
        jobManager = jobManager,
        producer = kafkaProducer,
    )
    outboxProcessor.start()
    // END outbox config

    val arrangorRepository = ArrangorRepository()
    val navAnsattRepository = NavAnsattRepository()
    val navEnhetRepository = NavEnhetRepository()
    val navBrukerRepository = NavBrukerRepository()
    val tiltakRepository = TiltakRepository()
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

    val navEnhetService = NavEnhetService(
        repository = navEnhetRepository,
        amtPersonServiceClient = amtPersonServiceClient,
    )

    val navAnsattService = NavAnsattService(
        repository = navAnsattRepository,
        amtPersonServiceClient = amtPersonServiceClient,
        navEnhetService = navEnhetService,
    )

    val navBrukerService = NavBrukerService(
        repository = navBrukerRepository,
        personServiceClient = amtPersonServiceClient,
        enhetService = navEnhetService,
        ansattService = navAnsattService,
    )

    val vurderingService = VurderingService(vurderingRepository)
    val arrangorService = ArrangorService(
        arrangorRepository = arrangorRepository,
        amtArrangorClient = amtArrangorClient,
    )

    val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
    val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
    val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository = deltakerEndringRepository,
        vedtakRepository = vedtakRepository,
        forslagRepository = forslagRepository,
        endringFraArrangorRepository = endringFraArrangorRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        innsokPaaFellesOppstartRepository = innsokPaaFellesOppstartRepository,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        vurderingRepository = vurderingRepository,
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

    val hendelseProducer = DistribuerEndringProducer(outboxService)

    val distribuerEndringService = DistribuerEndringService(
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

    val deltakerKafkaPayloadBuilder = DeltakerKafkaPayloadBuilder(
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        vurderingRepository = vurderingRepository,
    )

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

    val gjennomforingRequestProducer = GjennomforingRequestProducer(
        outboxService = outboxService,
    )

    val deltakerProducerService = DeltakerProducerService(
        deltakerKafkaPayloadBuilder = deltakerKafkaPayloadBuilder,
        deltakerProducer = deltakerProducer,
        deltakerV1Producer = deltakerV1Producer,
        deltakerEksternV1Producer = deltakerEksternV1Producer,
        unleashToggle = unleashToggle,
    )

    val vedtakService = VedtakService(vedtakRepository)

    val forslagService = ForslagService(
        forslagRepository = forslagRepository,
        arrangorMeldingProducer = ArrangorMeldingProducer(outboxService),
        deltakerRepository = deltakerRepository,
        deltakerProducerService = deltakerProducerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )

    val deltakerEndringService = DeltakerEndringService(
        deltakerEndringRepository = deltakerEndringRepository,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        distribuerEndringService = distribuerEndringService,
        forslagService = forslagService,
        deltakerHistorikkService = deltakerHistorikkService,
    )

    val deltakelserResponseMapper = DeltakelserResponseMapper(
        deltakerHistorikkService = deltakerHistorikkService,
        arrangorService = arrangorService,
    )

    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerProducerService = deltakerProducerService,
        vedtakRepository = vedtakRepository,
        vedtakService = vedtakService,
        distribuerEndringService = distribuerEndringService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navAnsattService = navAnsattService,
        forslagRepository = forslagRepository,
        unleashToggle = unleashToggle,
    )

    val endringFraArrangorService = EndringFraArrangorService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        distribuerEndringService = distribuerEndringService,
        deltakerHistorikkService = deltakerHistorikkService,
    )

    val opprettKladdRequestValidator = OpprettKladdRequestValidator(
        deltakerlisteRepository = deltakerlisteRepository,
        brukerService = navBrukerService,
        personServiceClient = amtPersonServiceClient,
        isOppfolgingsTilfelleClient = isOppfolgingsTilfelleClient,
    )

    val kladdService = KladdService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = navBrukerService,
        deltakerlisteRepository = deltakerlisteRepository,
    )

    val enkeltplassService = EnkeltplassService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        gjennomforingRequestProducer = gjennomforingRequestProducer,
        deltakerlisteRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        tiltakRepository = tiltakRepository,
        navEnhetService = navEnhetService,
        navEnhetRepository = navEnhetRepository,
        navAnsattService = navAnsattService,
        navAnsattRepository = navAnsattRepository,
        vedtakService = vedtakService,
        arrangorService = arrangorService,
        kodeverkClient = kodeverkClient,
    )

    val pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
        distribuerEndringService = distribuerEndringService,
        innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
        enkeltplassService = enkeltplassService,
        kodeverkClient = kodeverkClient,
    )

    val deltakerLaaseService = DeltakerLaaseService(
        deltakerRepository = deltakerRepository,
    )

    val tiltaksansvarligService = TiltaksansvarligService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        deltakerProducerService = deltakerProducerService,
        distribuerEndringService = distribuerEndringService,
        vedtakService = vedtakService,
    )

    val digitalBrukerService = DigitalBrukerService(
        amtDistribusjonClient = amtDistribusjonClient,
    )

    val deltakerResponseBuilder = DeltakerResponseBuilder(
        arrangorService = arrangorService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        digitalBrukerService = digitalBrukerService,
        deltakerHistorikkService = deltakerHistorikkService,
        forslagRepository = forslagRepository,
        deltakerLaaseService = deltakerLaaseService,
        vurderingRepository = vurderingRepository,
        deltakerRepository = deltakerRepository,
    )

    val tiltakskoordinatorViewRepository = TiltakskoordinatorViewRepository()

    val tiltakskoordinatorResponseBuilder = TiltakskoordinatorResponseBuilder(
        viewRepository = tiltakskoordinatorViewRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        digitalBrukerService = digitalBrukerService,
        deltakerLaaseService = deltakerLaaseService,
    )

    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        NavAnsattConsumer(navAnsattRepository, navAnsattService),
        NavBrukerConsumer(navBrukerRepository, navEnhetService, deltakerService),
        TiltakConsumer(tiltakRepository),
        GjennomforingConsumer(
            deltakerlisteRepository = deltakerlisteRepository,
            deltakerRepository = deltakerRepository,
            tiltakRepository = tiltakRepository,
            arrangorService = arrangorService,
            deltakerService = deltakerService,
            deltakerProducerService = deltakerProducerService,
            unleashToggle = unleashToggle,
        ),
        EnkeltplassDeltakerConsumer(
            deltakerRepository,
            deltakerService,
            deltakerlisteRepository,
            navBrukerService,
            importertFraArenaRepository,
            unleashToggle,
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
        deltakerlisteRepository = deltakerlisteRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        tilgangskontrollService = tilgangskontrollService,
        deltakelserResponseMapper = deltakelserResponseMapper,
        deltakerProducerService = deltakerProducerService,
        vedtakService = vedtakService,
        unleashToggle = unleashToggle,
        innsokPaaFellesOppstartRepository = innsokPaaFellesOppstartRepository,
        vurderingRepository = vurderingRepository,
        distribuerEndringService = distribuerEndringService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navEnhetService = navEnhetService,
        navAnsattService = navAnsattService,
        vedtakRepository = vedtakRepository,
        deltakerResponseBuilder = deltakerResponseBuilder,
        tiltakskoordinatorResponseBuilder = tiltakskoordinatorResponseBuilder,
        kladdService = kladdService,
        enkeltplassService = enkeltplassService,
        arrangorService = arrangorService,
        gjennomforingRequestProducer = gjennomforingRequestProducer,
        tiltaksansvarligService = tiltaksansvarligService,
        forslagService = forslagService,
        forslagRepository = forslagRepository,
    )
    configureMonitoring()

    val statusUpdateJob = StatusUpdateJob(
        leaderElection = leaderElection,
        attributes = attributes,
        deltakerService = deltakerService,
    )
    statusUpdateJob.startJob()

    val deltakelsesmengdeUpdateJob = DeltakelsesmengdeUpdateJob(
        leaderElection = leaderElection,
        attributes = attributes,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
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
