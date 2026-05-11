package no.nav.amt.deltaker.bff.utils

import io.getunleash.Unleash
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.EnkeltplassClient
import no.nav.amt.deltaker.bff.clients.GjennomforingClient
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.clients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.testdata.TestdataService
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

abstract class IntegrationTestBase {
    protected val amtDeltakerClient = mockk<AmtDeltakerClient>()
    protected val amtDistribusjonClient: AmtDistribusjonClient = mockk()
    protected val arrangorsokClient = mockk<ArrangorsokClient>()
    protected val enkeltplassClient = mockk<EnkeltplassClient>()
    protected val paameldingClient: PaameldingClient = mockk()
    protected val gjennomforingClient: GjennomforingClient = mockk()
    protected val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected val kodeverkClient = mockk<KodeverkClient>()

    protected val deltakerRepository: DeltakerRepository = mockk()
    protected val forslagRepository: ForslagRepository = mockk()
    protected val tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository = mockk()

    protected val deltakerService: DeltakerService = mockk()
    protected val pameldingService: PameldingService = mockk()
    protected val navAnsattService: NavAnsattService = mockk()
    protected val navEnhetService: NavEnhetService = mockk()
    protected val forslagService: ForslagService = mockk()
    protected val sporbarhetsloggService: SporbarhetsloggService = mockk()
    protected val deltakerlisteService: DeltakerlisteService = mockk()

    protected val tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService = mockk()
    protected val tiltakskoordinatorService: TiltakskoordinatorService = mockk()
    protected val ulestHendelseService: UlestHendelseService = mockk()
    protected val testdataService: TestdataService = mockk()
    protected val selfServiceTilgangskontrollService: SelfServiceTilgangService = mockk()
    protected open val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient = poaoTilgangCachedClient,
    )

    protected val unleash: Unleash = mockk()
    protected val commonUnleashToggle: CommonUnleashToggle = mockk()

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

    protected fun <T : Any> withTestApplicationContext(
        appIsReady: Boolean = true, // for readiness-tester
        block: suspend (HttpClient) -> T,
    ): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(Environment())
                configureRequestValidation()
                configureRouting(
                    tilgangskontrollService = tilgangskontrollService,
                    deltakerService = deltakerService,
                    pameldingService = pameldingService,
                    navAnsattService = navAnsattService,
                    navEnhetService = navEnhetService,
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
                    tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
                    tiltakskoordinatorService = tiltakskoordinatorService,
                    tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
                    ulestHendelseService = ulestHendelseService,
                    testdataService = testdataService,
                    paameldingClient = paameldingClient,
                    gjennomforingClient = gjennomforingClient,
                    kodeverkClient = kodeverkClient,
                    selfServiceTilgangService = selfServiceTilgangskontrollService,
                )

                attributes.put(isReadyKey, appIsReady)
            }

            result = block(
                createClient {
                    install(ContentNegotiation) { jackson() }
                },
            )
        }

        return result
    }
}
