package no.nav.amt.deltaker.utils

import io.getunleash.FakeUnleash
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import org.junit.jupiter.api.BeforeEach

abstract class RouteTestBase {
    protected open val deltakelserResponseMapper: DeltakelserResponseMapper = mockk(relaxed = true)

    protected val pameldingService: PameldingService = mockk(relaxed = true)
    protected val deltakerService: DeltakerService = mockk(relaxed = true)
    protected val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    protected val deltakerHistorikkService: DeltakerHistorikkService = mockk(relaxed = true)
    protected val deltakerProducerService: DeltakerProducerService = mockk(relaxed = true)
    protected val vedtakService: VedtakService = mockk(relaxed = true)
    protected val innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository = mockk(relaxed = true)
    protected val vurderingRepository: VurderingRepository = mockk(relaxed = true)
    protected val hendelseService: HendelseService = mockk(relaxed = true)
    protected val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository = mockk(relaxed = true)
    protected val arrangorService = mockk<ArrangorService>()
    protected val navEnhetService = mockk<NavEnhetService>()
    protected val navAnsattService = mockk<NavAnsattService>()
    protected val vedtakRepository = mockk<VedtakRepository>()

    protected val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    protected val unleashClient = FakeUnleash()
    protected open val unleashToggle = CommonUnleashToggle(unleashClient)

    protected val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()

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
                configureRequestValidation(
                    opprettKladdRequestValidator = opprettKladdRequestValidator,
                )
                configureRouting(
                    pameldingService,
                    deltakerService,
                    deltakerRepository,
                    deltakerHistorikkService,
                    tilgangskontrollService,
                    deltakelserResponseMapper,
                    deltakerProducerService,
                    vedtakService,
                    unleashToggle,
                    innsokPaaFellesOppstartRepository,
                    vurderingRepository,
                    hendelseService,
                    endringFraTiltakskoordinatorRepository,
                    navEnhetService,
                    vedtakRepository,
                    navAnsattService,
                )
                setUpTestRoute()
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

    private fun Application.setUpTestRoute() {
        routing {
            authenticate("SYSTEM") {
                get("/deltaker") {
                    call.respondText("System har tilgang!")
                }
            }
        }
    }
}
