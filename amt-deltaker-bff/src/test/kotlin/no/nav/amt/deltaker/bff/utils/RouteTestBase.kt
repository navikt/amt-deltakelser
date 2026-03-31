package no.nav.amt.deltaker.bff.utils

import io.getunleash.Unleash
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.apiclients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.EnkeltplassClient
import no.nav.amt.deltaker.bff.apiclients.PaameldingClient
import no.nav.amt.deltaker.bff.apiclients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorsDeltakerlisteProducer
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
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

abstract class RouteTestBase {
    protected val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    protected val deltakerService: DeltakerService = mockk(relaxed = true)
    protected val pameldingService: PameldingService = mockk(relaxed = true)
    protected val paameldingClient: PaameldingClient = mockk(relaxed = true)
    protected val navAnsattService: NavAnsattService = mockk(relaxed = true)
    protected val navEnhetService: NavEnhetService = mockk(relaxed = true)
    protected val innbyggerService: InnbyggerService = mockk(relaxed = true)
    protected val forslagRepository: ForslagRepository = mockk(relaxed = true)
    protected val forslagService: ForslagService = mockk(relaxed = true)

    protected val amtDistribusjonClient: AmtDistribusjonClient = mockk(relaxed = true)
    protected val amtDeltakerClient = mockk<AmtDeltakerClient>(relaxed = true)
    protected val arrangorsokClient = mockk<ArrangorsokClient>(relaxed = true)

    protected val enkeltplassClient = mockk<EnkeltplassClient>()

    protected val sporbarhetsloggService: SporbarhetsloggService = mockk(relaxed = true)
    protected val deltakerlisteService: DeltakerlisteService = mockk(relaxed = true)
    protected val unleash: Unleash = mockk(relaxed = true)
    protected val commonUnleashToggle: CommonUnleashToggle = mockk(relaxed = true)
    protected val sporbarhetOgTilgangskontrollSvc: SporbarhetOgTilgangskontrollSvc = mockk(relaxed = true)
    protected val tiltakskoordinatorService: TiltakskoordinatorService = mockk(relaxed = true)
    protected val tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository = mockk(relaxed = true)
    protected val ulestHendelseService: UlestHendelseService = mockk(relaxed = true)
    protected val testdataService: TestdataService = mockk(relaxed = true)
    protected val tiltakskoordinatorsDeltakerlisteProducer = mockk<TiltakskoordinatorsDeltakerlisteProducer>()
    protected val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected open val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient = poaoTilgangCachedClient,
        navAnsattService = navAnsattService,
        tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer = tiltakskoordinatorsDeltakerlisteProducer,
        tiltakskoordinatorService = tiltakskoordinatorService,
        deltakerlisteService = deltakerlisteService,
    )

    @BeforeEach
    protected fun init() {
        clearAllMocks()
        configureEnvForAuthentication()
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
    }

    val bearerTokenInTest = generateJWT(
        consumerClientId = "frontend-clientid",
        navAnsattAzureId = UUID.randomUUID().toString(),
        audience = "deltaker-bff",
        groups = listOf(UUID(0L, 0L).toString()),
    )

    protected fun <T : Any> withTestApplicationContext(block: suspend (HttpClient) -> T): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(Environment())
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
                    arrangorsokClient = arrangorsokClient,
                    enkeltplassClient = enkeltplassClient,
                    sporbarhetsloggService = sporbarhetsloggService,
                    deltakerRepository = deltakerRepository,
                    deltakerlisteService = deltakerlisteService,
                    unleash = unleash,
                    commonUnleashToggle = commonUnleashToggle,
                    sporbarhetOgTilgangskontrollSvc = sporbarhetOgTilgangskontrollSvc,
                    tiltakskoordinatorService = tiltakskoordinatorService,
                    tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
                    ulestHendelseService = ulestHendelseService,
                    testdataService = testdataService,
                    paameldingClient = paameldingClient,
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
