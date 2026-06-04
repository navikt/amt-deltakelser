package no.nav.amt.distribusjon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.Environment.Companion.HTTP_CONNECT_TIMEOUT_MILLIS
import no.nav.amt.distribusjon.Environment.Companion.HTTP_REQUEST_TIMEOUT_MILLIS
import no.nav.amt.distribusjon.Environment.Companion.HTTP_SOCKET_TIMEOUT_MILLIS
import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.application.plugins.configureAuthentication
import no.nav.amt.distribusjon.application.plugins.configureMonitoring
import no.nav.amt.distribusjon.application.plugins.configureRouting
import no.nav.amt.distribusjon.application.plugins.configureSerialization
import no.nav.amt.distribusjon.arrangormelding.ArrangorMeldingConsumer
import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService
import no.nav.amt.distribusjon.distribusjonskanal.DokdistkanalClient
import no.nav.amt.distribusjon.hendelse.HendelseConsumer
import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
import no.nav.amt.distribusjon.journalforing.dokarkiv.DokarkivClient
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient
import no.nav.amt.distribusjon.journalforing.job.EndringsvedtakJob
import no.nav.amt.distribusjon.journalforing.pdf.PdfgenClient
import no.nav.amt.distribusjon.journalforing.person.AmtPersonClient
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseProducer
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseRepository
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseService
import no.nav.amt.distribusjon.varsel.VarselJobService
import no.nav.amt.distribusjon.varsel.VarselOutboxHandler
import no.nav.amt.distribusjon.varsel.VarselRepository
import no.nav.amt.distribusjon.varsel.VarselService
import no.nav.amt.distribusjon.varsel.hendelse.VarselHendelseConsumer
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.job.JobManager
import no.nav.amt.lib.utils.leaderelection.Leader
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import no.nav.amt.lib.utils.leaderelection.LeaderProvider
import kotlin.time.Duration.Companion.seconds

val env = Environment()

fun main() {
    embeddedServer(
        factory = Netty,
        configure = {
            connector { port = env.port }
            shutdownGracePeriod = 10.seconds.inWholeMilliseconds
            shutdownTimeout = 20.seconds.inWholeMilliseconds
        },
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()

    val environment = env

    Database.init(config = environment.databaseConfig)

    val httpClient = HttpClient(CIO) {
        engine {
            endpoint {
                keepAliveTime = 0
            }
        }

        install(ContentNegotiation) {
            jackson()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MILLIS
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()

            retryIf { _, response ->
                !response.status.isSuccess()
            }

            retryOnExceptionIf { _, cause ->
                when (cause) {
                    is java.io.EOFException,
                    is java.net.ConnectException,
                    is java.net.SocketTimeoutException,
                    is io.ktor.client.plugins.HttpRequestTimeoutException,
                    -> true

                    else -> false
                }
            }
        }
    }

    val leaderProvider = LeaderProvider { path ->
        httpClient.get(path).body<Leader>()
    }

    val leaderElection = LeaderElectionClient(leaderProvider, environment.leaderElectorUrl)
    val jobManager = JobManager(leaderElection::isLeader, ::isReady)

    val azureAdTokenClient = AzureAdTokenClient(
        azureAdTokenUrl = environment.azureAdTokenUrl,
        clientId = environment.azureClientId,
        clientSecret = environment.azureClientSecret,
        httpClient = httpClient,
    )

    val pdfgenClient = PdfgenClient(httpClient, env)
    val amtPersonClient = AmtPersonClient(httpClient, azureAdTokenClient, env)
    val amtDeltakerClient = AmtDeltakerClient(
        baseUrl = environment.amtDeltakerUrl,
        scope = environment.amtDeltakerScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val veilarboppfolgingClient = VeilarboppfolgingClient(httpClient, azureAdTokenClient, env)
    val dokarkivClient = DokarkivClient(httpClient, azureAdTokenClient, env)
    val dokdistkanalClient = DokdistkanalClient(httpClient, azureAdTokenClient, env)
    val dokdistfordelingClient = DokdistfordelingClient(httpClient, azureAdTokenClient, env)

    val digitalBrukerService = DigitalBrukerService(dokdistkanalClient, veilarboppfolgingClient)

    val kafkaProducer = Producer<String, String>(
        if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
    )

    val outboxService = OutboxService()
    val outboxProcessor = OutboxProcessor(outboxService, jobManager, kafkaProducer)

    val hendelseRepository = HendelseRepository()
    val varselRepository = VarselRepository()

    val varselService = VarselService(
        varselRepository = VarselRepository(),
        hendelseRepository = hendelseRepository,
        outboxHandler = VarselOutboxHandler(outboxService),
    )

    val journalforingService = JournalforingService(
        JournalforingstatusRepository(),
        amtPersonClient,
        pdfgenClient,
        veilarboppfolgingClient,
        dokarkivClient,
        dokdistfordelingClient,
        amtDeltakerClient,
    )

    val tiltakshendelseService = TiltakshendelseService(
        tiltakshendelseRepository = TiltakshendelseRepository(),
        amtDeltakerClient = amtDeltakerClient,
        tiltakshendelseProducer = TiltakshendelseProducer(outboxService),
    )

    val consumers = listOf(
        HendelseConsumer(
            varselService,
            journalforingService,
            tiltakshendelseService,
            hendelseRepository,
            dokdistkanalClient,
            veilarboppfolgingClient,
        ),
        VarselHendelseConsumer(varselRepository, varselService),
        ArrangorMeldingConsumer(tiltakshendelseService),
    )
    consumers.forEach { it.start() }

    configureAuthentication(environment)
    configureRouting(digitalBrukerService, tiltakshendelseService)
    configureMonitoring()

    val endringsvedtakJob = EndringsvedtakJob(jobManager, hendelseRepository, journalforingService)
    endringsvedtakJob.startJob()

    val varselJobService = VarselJobService(jobManager, varselService)
    varselJobService.startJobs()

    outboxProcessor.start()

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

fun Application.isReady() = attributes.getOrNull(isReadyKey) == true
