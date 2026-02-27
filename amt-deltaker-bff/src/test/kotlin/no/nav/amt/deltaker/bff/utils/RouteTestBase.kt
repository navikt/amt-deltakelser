package no.nav.amt.deltaker.bff.utils

import io.getunleash.Unleash
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.innbygger.InnbyggerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.deltaker.bff.testdata.TestdataService
import no.nav.amt.deltaker.bff.tiltakskoordinator.SporbarhetOgTilgangskontrollSvc
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import no.nav.amt.lib.utils.applicationConfig
import org.junit.jupiter.api.BeforeEach

abstract class RouteTestBase {
    protected val tilgangskontrollService: TilgangskontrollService = mockk(relaxed = true)
    protected val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    protected val deltakerService: DeltakerService = mockk(relaxed = true)
    protected val pameldingService: PameldingService = mockk(relaxed = true)
    protected val navAnsattService: NavAnsattService = mockk(relaxed = true)
    protected val navEnhetService: NavEnhetService = mockk(relaxed = true)
    protected val innbyggerService: InnbyggerService = mockk(relaxed = true)
    protected val forslagRepository: ForslagRepository = mockk(relaxed = true)
    protected val forslagService: ForslagService = mockk(relaxed = true)
    protected val amtDistribusjonClient: AmtDistribusjonClient = mockk(relaxed = true)
    protected val sporbarhetsloggService: SporbarhetsloggService = mockk(relaxed = true)
    protected val deltakerlisteService: DeltakerlisteService = mockk(relaxed = true)
    protected val unleash: Unleash = mockk(relaxed = true)
    protected val sporbarhetOgTilgangskontrollSvc: SporbarhetOgTilgangskontrollSvc = mockk(relaxed = true)
    protected val tiltakskoordinatorService: TiltakskoordinatorService = mockk(relaxed = true)
    protected val tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository = mockk(relaxed = true)
    protected val ulestHendelseService: UlestHendelseService = mockk(relaxed = true)
    protected val testdataService: TestdataService = mockk(relaxed = true)

    @BeforeEach
    protected fun init() {
        clearAllMocks()
        configureEnvForAuthentication()
    }

    protected fun <T : Any> withTestApplicationContext(block: suspend (HttpClient) -> T): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(Environment())
                configureRouting(
                    tilgangskontrollService,
                    deltakerService,
                    pameldingService,
                    navAnsattService,
                    navEnhetService,
                    innbyggerService,
                    forslagRepository,
                    forslagService,
                    amtDistribusjonClient,
                    sporbarhetsloggService,
                    deltakerRepository,
                    deltakerlisteService,
                    unleash,
                    sporbarhetOgTilgangskontrollSvc,
                    tiltakskoordinatorService,
                    tiltakskoordinatorTilgangRepository,
                    ulestHendelseService,
                    testdataService,
                )
            }

            result =
                block(
                    createClient {
                        install(ContentNegotiation) {
                            jackson { applicationConfig() }
                        }
                    },
                )
        }

        return result
    }
}
