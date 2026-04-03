package no.nav.amt.distribusjon

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.application.plugins.configureAuthentication
import no.nav.amt.distribusjon.application.plugins.configureRouting
import no.nav.amt.distribusjon.application.plugins.configureSerialization
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
import no.nav.amt.distribusjon.varsel.VarselOutboxHandler
import no.nav.amt.distribusjon.varsel.VarselRepository
import no.nav.amt.distribusjon.varsel.VarselService
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.applicationConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension

abstract class IntegrationTestBase {
    protected open val hendelseRepository = HendelseRepository()
    protected open val varselRepository = VarselRepository()
    protected open val journalforingstatusRepository = JournalforingstatusRepository()
    protected open val tiltakshendelseRepository = TiltakshendelseRepository()

    protected open val amtDeltakerClient: AmtDeltakerClient = mockk(relaxed = true)
    protected open val pdfgenClient: PdfgenClient = mockk(relaxed = true)
    protected open val amtPersonClient: AmtPersonClient = mockk(relaxed = true)
    protected open val veilarboppfolgingClient: VeilarboppfolgingClient = mockk(relaxed = true)
    protected open val dokarkivClient: DokarkivClient = mockk(relaxed = true)
    protected open val dokdistkanalClient: DokdistkanalClient = mockk(relaxed = true)
    protected open val dokdistfordelingClient: DokdistfordelingClient = mockk(relaxed = true)

    protected open val outboxService: OutboxService = mockk(relaxed = true)

    protected open val journalforingService = JournalforingService(
        journalforingstatusRepository = journalforingstatusRepository,
        amtPersonClient = amtPersonClient,
        pdfgenClient = pdfgenClient,
        veilarboppfolgingClient = veilarboppfolgingClient,
        dokarkivClient = dokarkivClient,
        dokdistfordelingClient = dokdistfordelingClient,
        amtDeltakerClient = amtDeltakerClient,
    )

    protected open val digitalBrukerService = DigitalBrukerService(
        dokdistkanalClient = dokdistkanalClient,
        veilarboppfolgingClient = veilarboppfolgingClient,
    )

    protected open val tiltakshendelseProducer = TiltakshendelseProducer(outboxService)

    protected open val tiltakshendelseService = TiltakshendelseService(
        tiltakshendelseRepository = tiltakshendelseRepository,
        amtDeltakerClient = amtDeltakerClient,
        tiltakshendelseProducer = tiltakshendelseProducer,
    )

    protected open val varselService = VarselService(
        varselRepository = varselRepository,
        hendelseRepository = hendelseRepository,
        outboxHandler = VarselOutboxHandler(outboxService),
    )

    protected open val hendelseConsumer = HendelseConsumer(
        varselService = varselService,
        journalforingService = journalforingService,
        tiltakshendelseService = tiltakshendelseService,
        hendelseRepository = hendelseRepository,
        dokdistkanalClient = dokdistkanalClient,
        veilarboppfolgingClient = veilarboppfolgingClient,
    )

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() = clearAllMocks()

    protected fun <T : Any> withTestApplicationContext(
        appIsReady: Boolean = true, // for readiness-tester
        block: suspend (HttpClient) -> T,
    ): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(testEnvironment)
                configureRouting(digitalBrukerService, tiltakshendelseService)

                attributes.put(isReadyKey, appIsReady)
            }

            result = block(
                createClient {
                    install(ContentNegotiation) { jackson { applicationConfig() } }
                },
            )
        }

        return result
    }
}
