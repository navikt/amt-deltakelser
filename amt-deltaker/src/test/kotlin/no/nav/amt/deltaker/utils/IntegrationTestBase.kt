package no.nav.amt.deltaker.utils

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
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.KladdService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.deltaker.ResponseBuilder
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.navtiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestBase {
    protected open val responseBuilder = mockk<ResponseBuilder>()

    protected open val arrangorClient: AmtArrangorClient = mockk()
    protected open val personServiceClient: AmtPersonServiceClient = mockk(relaxed = true)

    protected open val arrangorRepository: ArrangorRepository = mockk(relaxed = true)
    protected open val deltakerEndringRepository: DeltakerEndringRepository = mockk()
    protected open val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    protected open val deltakerlisteRepository: DeltakerlisteRepository = mockk(relaxed = true)
    protected open val endringFraArrangorRepository: EndringFraArrangorRepository = mockk()
    protected open val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository = mockk(relaxed = true)
    protected open val forslagRepository: ForslagRepository = mockk()
    protected open val importertFraArenaRepository: ImportertFraArenaRepository = mockk()
    protected open val innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository = mockk(relaxed = true)
    protected open val navAnsattRepository: NavAnsattRepository = mockk(relaxed = true)
    protected open val navBrukerRepository: NavBrukerRepository = mockk(relaxed = true)
    protected open val navEnhetRepository: NavEnhetRepository = mockk(relaxed = true)
    protected open val tiltakstypeRepository: TiltakstypeRepository = mockk(relaxed = true)
    protected open val vedtakRepository = mockk<VedtakRepository>()
    protected open val vurderingRepository: VurderingRepository = mockk(relaxed = true)

    protected open val navEnhetService: NavEnhetService by lazy {
        NavEnhetService(
            repository = navEnhetRepository,
            amtPersonServiceClient = personServiceClient,
        )
    }

    protected open val navAnsattService: NavAnsattService by lazy {
        NavAnsattService(
            repository = navAnsattRepository,
            navEnhetService = navEnhetService,
            amtPersonServiceClient = personServiceClient,
        )
    }

    protected open val navBrukerService: NavBrukerService by lazy {
        NavBrukerService(
            repository = navBrukerRepository,
            personServiceClient = personServiceClient,
            enhetService = navEnhetService,
            ansattService = navAnsattService,
        )
    }

    protected open val arrangorService: ArrangorService by lazy {
        ArrangorService(
            arrangorRepository = arrangorRepository,
            amtArrangorClient = arrangorClient,
        )
    }
    protected open val pameldingService: PameldingService = mockk(relaxed = true)
    protected open val kladdService: KladdService = mockk(relaxed = true)
    protected open val deltakerHistorikkService: DeltakerHistorikkService = mockk(relaxed = true)
    protected open val deltakerProducerService: DeltakerProducerService = mockk(relaxed = true)
    protected open val vedtakService: VedtakService = mockk(relaxed = true)
    protected open val hendelseService: HendelseService = mockk(relaxed = true)

    protected open val deltakelserResponseMapper: DeltakelserResponseMapper by lazy {
        DeltakelserResponseMapper(
            deltakerHistorikkService = deltakerHistorikkService,
            arrangorService = arrangorService,
        )
    }

    protected open val deltakerService: DeltakerService by lazy {
        DeltakerService(
            deltakerRepository = deltakerRepository,
            vedtakRepository = vedtakRepository,
            deltakerHistorikkService = deltakerHistorikkService,
            deltakerProducerService = deltakerProducerService,
            vedtakService = vedtakService,
            endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
            deltakerEndringRepository = deltakerEndringRepository,
            hendelseService = hendelseService,
            deltakerEndringService = mockk(relaxed = true),
            navEnhetService = navEnhetService,
            navAnsattService = navAnsattService,
            forslagRepository = forslagRepository,
            endringFraArrangorRepository = endringFraArrangorRepository,
            importertFraArenaRepository = importertFraArenaRepository,
        )
    }

    protected open val enkeltplassService: EnkeltplassService by lazy {
        EnkeltplassService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            gjennomforingRequestProducer = gjennomforingRequestProducer,
            deltakerlisteRepository = deltakerlisteRepository,
            navBrukerService = navBrukerService,
            tiltakstypeRepository = tiltakstypeRepository,
            navEnhetService = navEnhetService,
            navAnsattService = navAnsattService,
            vedtakService = vedtakService,
        )
    }

    protected open val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected open val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    protected open val unleashToggle: CommonUnleashToggle = mockk(relaxed = true)

    protected val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()

    protected open val deltakerlisteConsumer: DeltakerlisteConsumer by lazy {
        DeltakerlisteConsumer(
            deltakerlisteRepository = deltakerlisteRepository,
            deltakerRepository = deltakerRepository,
            tiltakstypeRepository = tiltakstypeRepository,
            arrangorService = arrangorService,
            deltakerService = deltakerService,
            unleashToggle = unleashToggle,
            deltakerProducerService = mockk(relaxed = true),
        )
    }

    protected open val gjennomforingRequestProducer = mockk<GjennomforingRequestProducer>(relaxUnitFun = true)

    @BeforeEach
    protected fun init() {
        clearAllMocks()
        configureEnvForAuthentication()
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
    }

    protected fun <T : Any> withTestApplicationContext(
        appIsReady: Boolean = true,
        block: suspend (HttpClient) -> T,
    ): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(Environment())
                configureRequestValidation(
                    opprettKladdRequestValidator = opprettKladdRequestValidator,
                )
                configureRouting(
                    pameldingService = pameldingService,
                    kladdService = kladdService,
                    enkeltplassService = enkeltplassService,
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
                    vedtakRepository = vedtakRepository,
                    navAnsattService = navAnsattService,
                    responseBuilder = responseBuilder,
                )
                setUpTestRoute()

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
