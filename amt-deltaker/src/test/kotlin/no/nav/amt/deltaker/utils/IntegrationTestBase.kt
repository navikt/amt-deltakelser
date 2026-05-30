package no.nav.amt.deltaker.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.api.external.response.DeltakelserResponseMapper
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder
import no.nav.amt.deltaker.application.plugins.OpprettKladdRequestValidator
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.clients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerCacheRepository
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.innbygger.DistribuerEndringProducer
import no.nav.amt.deltaker.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.kafka.GjennomforingConsumer
import no.nav.amt.deltaker.kafka.payload.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
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
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
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
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.outbox.OutboxRecord
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestBase {
    protected open val arrangorClient: AmtArrangorClient = mockk()
    protected open val personServiceClient: AmtPersonServiceClient = mockk()
    protected open val distribusjonClient: AmtDistribusjonClient = mockk()
    protected open val isOppfolgingstilfelleClient: IsOppfolgingstilfelleClient = mockk()
    protected open val kodeverkClient: KodeverkClient = mockk()

    protected open val arrangorRepository: ArrangorRepository = mockk()
    protected open val deltakerEndringRepository: DeltakerEndringRepository = mockk()
    protected open val deltakerRepository: DeltakerRepository = mockk()
    protected open val deltakerlisteRepository: DeltakerlisteRepository = mockk()
    protected open val endringFraArrangorRepository: EndringFraArrangorRepository = mockk()
    protected open val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository = mockk()
    protected open val forslagRepository: ForslagRepository = mockk()
    protected open val importertFraArenaRepository: ImportertFraArenaRepository = mockk()
    protected open val innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository = mockk()
    protected open val navAnsattRepository: NavAnsattRepository = mockk()
    protected open val navBrukerRepository: NavBrukerRepository = mockk()
    protected open val navEnhetRepository: NavEnhetRepository = mockk()
    protected open val tiltakRepository: TiltakRepository = mockk()
    protected open val vedtakRepository = mockk<VedtakRepository>()
    protected open val vurderingRepository: VurderingRepository = mockk()

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

    protected open val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService by lazy {
        InnsokPaaFellesOppstartService(repository = innsokPaaFellesOppstartRepository)
    }

    protected open val pameldingService: PameldingService by lazy {
        PameldingService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            navEnhetService = navEnhetService,
            navAnsattService = navAnsattService,
            vedtakService = vedtakService,
            distribuerEndringService = distribuerEndringService,
            innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
            enkeltplassService = enkeltplassService,
            kodeverkClient = kodeverkClient,
        )
    }

    protected open val kladdService: KladdService by lazy {
        KladdService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            deltakerlisteRepository = deltakerlisteRepository,
            navBrukerService = navBrukerService,
        )
    }

    protected open val vedtakService: VedtakService by lazy {
        VedtakService(vedtakRepository = vedtakRepository)
    }

    protected open val vurderingService: VurderingService by lazy {
        VurderingService(vurderingRepository = vurderingRepository)
    }

    protected open val hendelseProducer: DistribuerEndringProducer by lazy {
        DistribuerEndringProducer(outboxService = outboxService)
    }

    protected open val distribuerEndringService: DistribuerEndringService by lazy {
        DistribuerEndringService(
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
    }

    protected open val deltakerHistorikkService: DeltakerHistorikkService by lazy {
        DeltakerHistorikkService(
            deltakerEndringRepository = deltakerEndringRepository,
            vedtakRepository = vedtakRepository,
            forslagRepository = forslagRepository,
            endringFraArrangorRepository = endringFraArrangorRepository,
            importertFraArenaRepository = importertFraArenaRepository,
            innsokPaaFellesOppstartRepository = innsokPaaFellesOppstartRepository,
            endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
            vurderingRepository = vurderingRepository,
        )
    }

    protected open val deltakerKafkaPayloadBuilder: DeltakerKafkaPayloadBuilder by lazy {
        DeltakerKafkaPayloadBuilder(
            navAnsattRepository = navAnsattRepository,
            navEnhetRepository = navEnhetRepository,
            deltakerHistorikkService = deltakerHistorikkService,
            vurderingRepository = vurderingRepository,
        )
    }

    protected open val outboxService: OutboxService = mockk()
    protected open val stringStringProducer: Producer<String, String> = mockk()
    protected open val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected open val unleashToggle: CommonUnleashToggle = mockk()

    protected open val deltakerProducer: DeltakerProducer by lazy {
        DeltakerProducer(
            outboxService = outboxService,
            producer = stringStringProducer,
        )
    }

    protected open val deltakerV1Producer: DeltakerV1Producer by lazy {
        DeltakerV1Producer(
            outboxService = outboxService,
            producer = stringStringProducer,
        )
    }

    protected open val deltakerEksternV1Producer: DeltakerEksternV1Producer by lazy {
        DeltakerEksternV1Producer(
            outboxService = outboxService,
            producer = stringStringProducer,
        )
    }

    protected open val deltakerProducerService: DeltakerProducerService by lazy {
        DeltakerProducerService(
            deltakerKafkaPayloadBuilder = deltakerKafkaPayloadBuilder,
            deltakerProducer = deltakerProducer,
            deltakerV1Producer = deltakerV1Producer,
            deltakerEksternV1Producer = deltakerEksternV1Producer,
            unleashToggle = unleashToggle,
        )
    }

    protected open val deltakelserResponseMapper: DeltakelserResponseMapper by lazy {
        DeltakelserResponseMapper(
            deltakerHistorikkService = deltakerHistorikkService,
            arrangorService = arrangorService,
        )
    }

    protected open val arrangorMeldingProducer: ArrangorMeldingProducer by lazy {
        ArrangorMeldingProducer(outboxService = outboxService)
    }

    protected open val forslagService: ForslagService by lazy {
        ForslagService(
            forslagRepository = forslagRepository,
            arrangorMeldingProducer = arrangorMeldingProducer,
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
        )
    }

    protected open val deltakerEndringService: DeltakerEndringService by lazy {
        DeltakerEndringService(
            deltakerEndringRepository = deltakerEndringRepository,
            navAnsattRepository = navAnsattRepository,
            navEnhetRepository = navEnhetRepository,
            distribuerEndringService = distribuerEndringService,
            forslagService = forslagService,
            deltakerHistorikkService = deltakerHistorikkService,
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
            distribuerEndringService = distribuerEndringService,
            deltakerEndringService = deltakerEndringService,
            navAnsattService = navAnsattService,
            forslagRepository = forslagRepository,
            endringFraArrangorRepository = endringFraArrangorRepository,
            importertFraArenaRepository = importertFraArenaRepository,
            unleashToggle = unleashToggle,
        )
    }
    protected open val tiltaksansvarligService: TiltaksansvarligService by lazy {
        TiltaksansvarligService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
            vedtakService = vedtakService,
            endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
            distribuerEndringService = distribuerEndringService,
            navEnhetService = navEnhetService,
            navAnsattService = navAnsattService,
            deltakerService = deltakerService,
        )
    }

    protected open val enkeltplassService: EnkeltplassService by lazy {
        EnkeltplassService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            gjennomforingRequestProducer = gjennomforingRequestProducer,
            deltakerlisteRepository = deltakerlisteRepository,
            navBrukerService = navBrukerService,
            tiltakRepository = tiltakRepository,
            navEnhetService = navEnhetService,
            navAnsattService = navAnsattService,
            vedtakService = vedtakService,
            arrangorService = arrangorService,
            navEnhetRepository = navEnhetRepository,
            navAnsattRepository = navAnsattRepository,
            kodeverkClient = kodeverkClient,
        )
    }

    protected open val endringFraArrangorService: EndringFraArrangorService by lazy {
        EndringFraArrangorService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            endringFraArrangorRepository = endringFraArrangorRepository,
            distribuerEndringService = distribuerEndringService,
            deltakerHistorikkService = deltakerHistorikkService,
        )
    }

    protected open val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    protected open val opprettKladdRequestValidator: OpprettKladdRequestValidator by lazy {
        OpprettKladdRequestValidator(
            deltakerlisteRepository = deltakerlisteRepository,
            brukerService = navBrukerService,
            personServiceClient = personServiceClient,
            isOppfolgingsTilfelleClient = isOppfolgingstilfelleClient,
        )
    }

    protected open val gjennomforingConsumer: GjennomforingConsumer by lazy {
        GjennomforingConsumer(
            deltakerlisteRepository = deltakerlisteRepository,
            deltakerRepository = deltakerRepository,
            tiltakRepository = tiltakRepository,
            arrangorService = arrangorService,
            deltakerService = deltakerService,
            unleashToggle = unleashToggle,
            deltakerProducerService = deltakerProducerService,
        )
    }

    protected open val gjennomforingRequestProducer: GjennomforingRequestProducer by lazy {
        GjennomforingRequestProducer(outboxService = outboxService)
    }

    protected open val digitalBrukerService: DigitalBrukerService by lazy {
        DigitalBrukerService(
            amtDistribusjonClient = distribusjonClient,
        )
    }

    protected open val deltakerLaaseService: DeltakerLaaseService by lazy {
        DeltakerLaaseService(
            deltakerRepository = deltakerRepository,
        )
    }

    protected open val deltakerResponseBuilder: DeltakerResponseBuilder by lazy {
        DeltakerResponseBuilder(
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
    }

    protected open val tiltakskoordinatorViewRepository: TiltakskoordinatorViewRepository = mockk()

    protected open val tiltakskoordinatorResponseBuilder: TiltakskoordinatorResponseBuilder by lazy {
        TiltakskoordinatorResponseBuilder(
            viewRepository = tiltakskoordinatorViewRepository,
            deltakerlisteRepository = mockk(),
            digitalBrukerService = digitalBrukerService,
        )
    }

    @BeforeEach
    protected fun init() {
        clearAllMocks()
        configureEnvForAuthentication()

        mockkObject(DigitalBrukerCacheRepository)
        every { DigitalBrukerCacheRepository.hentForPersonidenter(any()) } returns emptyMap()
        every { DigitalBrukerCacheRepository.upsertBatch(any()) } returns Unit

        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalProdusereTilDeltakerEksternTopic() } returns true

        val mockOutboxRecord = mockk<OutboxRecord>()
        every {
            outboxService.insertRecord(any(), any(), any(), any())
        } returns mockOutboxRecord
    }

    @AfterEach
    protected fun teardown() {
        unmockkObject(DigitalBrukerCacheRepository)
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
                    distribuerEndringService = distribuerEndringService,
                    endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
                    navEnhetService = navEnhetService,
                    vedtakRepository = vedtakRepository,
                    navAnsattService = navAnsattService,
                    deltakerResponseBuilder = deltakerResponseBuilder,
                    tiltakskoordinatorResponseBuilder = tiltakskoordinatorResponseBuilder,
                    deltakerlisteRepository = deltakerlisteRepository,
                    arrangorService = arrangorService,
                    gjennomforingRequestProducer = gjennomforingRequestProducer,
                    tiltaksansvarligService = tiltaksansvarligService,
                )
                setUpTestRoute()

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
