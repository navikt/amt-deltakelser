package no.nav.amt.distribusjon

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.application.isReadyKey
import no.nav.amt.distribusjon.application.plugins.applicationConfig
import no.nav.amt.distribusjon.application.plugins.configureAuthentication
import no.nav.amt.distribusjon.application.plugins.configureRouting
import no.nav.amt.distribusjon.application.plugins.configureSerialization
import no.nav.amt.distribusjon.arrangormelding.ArrangorMeldingConsumer
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService
import no.nav.amt.distribusjon.distribusjonskanal.DokdistkanalClient
import no.nav.amt.distribusjon.hendelse.HendelseConsumer
import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
import no.nav.amt.distribusjon.journalforing.dokarkiv.DokarkivClient
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient
import no.nav.amt.distribusjon.journalforing.pdf.PdfgenClient
import no.nav.amt.distribusjon.journalforing.person.AmtPersonClient
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseProducer
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseRepository
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseService
import no.nav.amt.distribusjon.utils.mockAmtDeltakerClient
import no.nav.amt.distribusjon.utils.mockAmtPersonClient
import no.nav.amt.distribusjon.utils.mockAzureAdClient
import no.nav.amt.distribusjon.utils.mockDokarkivClient
import no.nav.amt.distribusjon.utils.mockDokdistfordelingClient
import no.nav.amt.distribusjon.utils.mockDokdistkanalClient
import no.nav.amt.distribusjon.utils.mockPdfgenClient
import no.nav.amt.distribusjon.utils.mockVeilarboppfolgingClient
import no.nav.amt.distribusjon.varsel.VarselOutboxHandler
import no.nav.amt.distribusjon.varsel.VarselRepository
import no.nav.amt.distribusjon.varsel.VarselService
import no.nav.amt.distribusjon.varsel.hendelse.VarselHendelseConsumer
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.outbox.OutboxRecord
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.TestPostgresContainer
import java.util.UUID

class TestApp {
    val varselRepository: VarselRepository
    val varselService: VarselService

    val azureAdTokenClient: AzureAdTokenClient

    val journalforingstatusRepository: JournalforingstatusRepository
    val hendelseRepository: HendelseRepository

    val pdfgenClient: PdfgenClient
    val amtPersonClient: AmtPersonClient
    val veilarboppfolgingClient: VeilarboppfolgingClient
    val dokarkivClient: DokarkivClient
    val dokdistkanalClient: DokdistkanalClient
    val dokdistfordelingClient: DokdistfordelingClient

    val journalforingService: JournalforingService
    val digitalBrukerService: DigitalBrukerService

    val tiltakshendelseRepository: TiltakshendelseRepository
    val tiltakshendelseService: TiltakshendelseService
    val tiltakshendelseProducer: TiltakshendelseProducer
    val amtDeltakerClient: AmtDeltakerClient

    val outboxService: OutboxService

    val environment: Environment = testEnvironment

    init {
        TestPostgresContainer.bootstrap()

        outboxService = OutboxService()

        azureAdTokenClient = mockAzureAdClient(environment)
        pdfgenClient = mockPdfgenClient(environment)
        amtPersonClient = mockAmtPersonClient(azureAdTokenClient, environment)
        veilarboppfolgingClient = mockVeilarboppfolgingClient(
            azureAdTokenClient,
            environment,
        )
        dokarkivClient = mockDokarkivClient(azureAdTokenClient, environment)
        dokdistkanalClient = mockDokdistkanalClient(azureAdTokenClient, environment)
        dokdistfordelingClient = mockDokdistfordelingClient(azureAdTokenClient, environment)
        amtDeltakerClient = mockAmtDeltakerClient(azureAdTokenClient, environment)
        journalforingstatusRepository = JournalforingstatusRepository()
        hendelseRepository = HendelseRepository()
        varselRepository = VarselRepository()

        varselService = VarselService(
            varselRepository = varselRepository,
            hendelseRepository = hendelseRepository,
            outboxHandler = VarselOutboxHandler(outboxService),
        )

        journalforingService = JournalforingService(
            journalforingstatusRepository,
            amtPersonClient,
            pdfgenClient,
            veilarboppfolgingClient,
            dokarkivClient,
            dokdistfordelingClient,
            amtDeltakerClient,
        )

        digitalBrukerService = DigitalBrukerService(dokdistkanalClient, veilarboppfolgingClient)
        tiltakshendelseProducer = TiltakshendelseProducer(outboxService)
        tiltakshendelseRepository = TiltakshendelseRepository()
        tiltakshendelseService = TiltakshendelseService(
            tiltakshendelseRepository = tiltakshendelseRepository,
            amtDeltakerClient = amtDeltakerClient,
            tiltakshendelseProducer = tiltakshendelseProducer,
        )

        val consumerId = UUID.randomUUID().toString()
        val kafkaConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost())
        val consumers = listOf(
            HendelseConsumer(
                varselService,
                journalforingService,
                tiltakshendelseService,
                hendelseRepository,
                dokdistkanalClient,
                veilarboppfolgingClient,
                consumerId,
                kafkaConfig,
            ),
            VarselHendelseConsumer(
                varselRepository = varselRepository,
                varselService = varselService,
                groupId = consumerId,
                kafkaConfig = kafkaConfig,
            ),
            ArrangorMeldingConsumer(tiltakshendelseService),
        )

        consumers.forEach { it.start() }
    }

    fun assertProducedInaktiver(id: UUID) {
        this should haveOutboxRecord(id, Environment.MINSIDE_VARSEL_TOPIC) {
            val json = it.value
            json["varselId"].asText() == id.toString() &&
                json["@event_name"].asText() == "inaktiver"
        }
    }

    fun assertProducedOppgave(id: UUID) {
        this should haveOutboxRecord(id, Environment.MINSIDE_VARSEL_TOPIC) { record ->
            val json = record.value

            json["varselId"].asText() == id.toString() &&
                json["@event_name"].asText() == "opprett" &&
                json["type"].asText() == "oppgave"
        }
    }

    fun assertProducedBeskjed(id: UUID, forventetUrl: String) {
        this should haveOutboxRecord(id, Environment.MINSIDE_VARSEL_TOPIC) { record ->
            val json = record.value
            json["varselId"].asText() == id.toString() &&
                json["@event_name"].asText() == "opprett" &&
                json["type"].asText() == "beskjed" &&
                json["link"].asText() == forventetUrl
        }
    }

    fun assertNotProducedHendelse(id: UUID) {
        this shouldNot haveOutboxRecord(id, Environment.MINSIDE_VARSEL_TOPIC)
    }
}

private val testApp = TestApp()

fun integrationTest(appShouldBeReady: Boolean = true, testBlock: suspend (app: TestApp, client: HttpClient) -> Unit) = testApplication {
    application {
        configureSerialization()

        configureAuthentication(testApp.environment)
        configureRouting(testApp.digitalBrukerService, testApp.tiltakshendelseService)
        // configureMonitoring()

        if (appShouldBeReady) attributes.put(isReadyKey, true)
    }

    testBlock(
        testApp,
        createClient {
            install(ContentNegotiation) {
                jackson { applicationConfig() }
            }
        },
    )
}

fun haveOutboxRecord(
    key: Any,
    topic: String,
    additionalConditions: (record: OutboxRecord) -> Boolean = { true },
) = object : Matcher<TestApp> {
    override fun test(value: TestApp): MatcherResult {
        val records = value.outboxService.getRecordsByTopicAndKey(topic, key.toString())
        val passed = records.any(additionalConditions)
        return MatcherResult(
            passed,
            {
                if (records.isEmpty()) {
                    "Expected an outbox record with key `$key` for topic `$topic`, but found none."
                } else {
                    "Outbox record with key `$key` for topic `$topic` did not meet additional conditions."
                }
            },
            { "Expected no outbox record with key `$key` for topic `$topic` that meets the conditions, but found one." },
        )
    }
}
