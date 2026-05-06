package no.nav.amt.deltaker.application.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.amt.deltaker.api.external.registerExternalApi
import no.nav.amt.deltaker.api.external.response.DeltakelserResponseMapper
import no.nav.amt.deltaker.api.registerEnkeltplassApi
import no.nav.amt.deltaker.api.registerGjennomforingApi
import no.nav.amt.deltaker.api.registerInternalApi
import no.nav.amt.deltaker.api.registerKladdApi
import no.nav.amt.deltaker.api.registerPameldingApi
import no.nav.amt.deltaker.api.registerVeilederApi
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.api.tiltaksansvarlig.registerTiltakskoordinatorApi
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.deltaker.veileder.PameldingService
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.ktor.routing.registerHealthApi
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Application.configureRequestValidation(opprettKladdRequestValidator: OpprettKladdRequestValidator) {
    install(RequestValidation) {
        validate<OpprettKladdRequest> { request ->
            opprettKladdRequestValidator.validateRequest(request)
        }
    }
}

fun Application.configureRouting(
    pameldingService: PameldingService,
    kladdService: KladdService,
    enkeltplassService: EnkeltplassService,
    deltakerService: DeltakerService,
    tiltaksansvarligService: TiltaksansvarligService,
    deltakerRepository: DeltakerRepository,
    deltakerlisteRepository: DeltakerlisteRepository,
    deltakerHistorikkService: DeltakerHistorikkService,
    tilgangskontrollService: TilgangskontrollService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    unleashToggle: CommonUnleashToggle,
    innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository,
    vurderingRepository: VurderingRepository,
    distribuerEndringService: DistribuerEndringService,
    endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    navEnhetService: NavEnhetService,
    vedtakRepository: VedtakRepository,
    navAnsattService: NavAnsattService,
    responseBuilder: ResponseBuilder,
    arrangorService: ArrangorService,
    gjennomforingRequestProducer: GjennomforingRequestProducer,
) {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.BadRequest, call, cause)
            call.respondText(text = "400: ${cause.message}", status = HttpStatusCode.BadRequest)
        }
        exception<AuthenticationException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.Unauthorized, call, cause)
            call.respondText(text = "401: ${cause.message}", status = HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.Forbidden, call, cause)
            call.respondText(text = "403: ${cause.message}", status = HttpStatusCode.Forbidden)
        }
        exception<NoSuchElementException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.NotFound, call, cause)
            call.respondText(text = "404: ${cause.message}", status = HttpStatusCode.NotFound)
        }
        exception<Throwable> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.InternalServerError, call, cause)
            call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
        exception<RequestValidationException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.BadRequest, call, cause)
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
        }
    }
    routing {
        registerHealthApi()

        registerPameldingApi(
            pameldingService = pameldingService,
            historikkService = deltakerHistorikkService,
        )
        registerKladdApi(
            kladdService = kladdService,
            deltakerRepository = deltakerRepository,
        )
        registerVeilederApi(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            historikkService = deltakerHistorikkService,
            responseBuilder = responseBuilder,
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            arrangorService = arrangorService,
        )
        registerGjennomforingApi(deltakerlisteRepository, responseBuilder)
        registerEnkeltplassApi(
            enkeltplassService = enkeltplassService,
            responseBuilder = responseBuilder,
        )
        registerInternalApi(
            deltakerRepository,
            deltakerService,
            kladdService,
            deltakerProducerService,
            vedtakService,
            innsokPaaFellesOppstartRepository,
            vurderingRepository,
            distribuerEndringService,
            endringFraTiltakskoordinatorRepository,
            vedtakRepository,
            navAnsattService,
            navEnhetService,
            gjennomforingRequestProducer,
        )

        registerTiltakskoordinatorApi(tiltaksansvarligService, deltakerHistorikkService)
        registerExternalApi(deltakerRepository, navEnhetService, tilgangskontrollService, deltakelserResponseMapper, unleashToggle)

        val catchAllRoute = "{...}"
        route(catchAllRoute) {
            handle {
                StatusPageLogger.log(
                    HttpStatusCode.NotFound,
                    this.call,
                    NoSuchElementException("Endepunktet eksisterer ikke"),
                )
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

object StatusPageLogger {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    fun log(
        statusCode: HttpStatusCode,
        call: ApplicationCall,
        cause: Throwable,
    ) {
        val msg = "${statusCode.value} ${statusCode.description}: " +
            "${call.request.httpMethod.value} ${call.request.path()}\n" +
            "Error: ${cause.message}"

        when (statusCode.value) {
            in 100..399 -> log.info(msg)
            in 400..404 -> log.warn(msg)
            else -> log.error(msg, cause)
        }
    }
}
