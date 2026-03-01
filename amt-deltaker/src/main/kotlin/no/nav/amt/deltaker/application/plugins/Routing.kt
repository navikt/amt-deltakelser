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
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.deltaker.ResponseBuilder
import no.nav.amt.deltaker.deltaker.api.deltaker.registerDeltakerApi
import no.nav.amt.deltaker.deltaker.api.paamelding.registerPameldingApi
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.external.api.registerExternalApi
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.internal.registerInternalApi
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.api.registerTiltakskoordinatorApi
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.ktor.routing.registerHealthApi
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
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
    deltakerService: DeltakerService,
    deltakerRepository: DeltakerRepository,
    deltakerHistorikkService: DeltakerHistorikkService,
    tilgangskontrollService: TilgangskontrollService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    unleashToggle: CommonUnleashToggle,
    innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository,
    vurderingRepository: VurderingRepository,
    hendelseService: HendelseService,
    endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    navEnhetService: NavEnhetService,
    vedtakRepository: VedtakRepository,
    navAnsattService: NavAnsattService,
    responseBuilder: ResponseBuilder,
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
        registerDeltakerApi(deltakerRepository, deltakerService, deltakerHistorikkService, responseBuilder)
        registerInternalApi(
            deltakerRepository,
            deltakerService,
            pameldingService,
            deltakerProducerService,
            vedtakService,
            innsokPaaFellesOppstartRepository,
            vurderingRepository,
            hendelseService,
            endringFraTiltakskoordinatorRepository,
            vedtakRepository,
            navAnsattService,
            navEnhetService,
        )
        registerTiltakskoordinatorApi(deltakerService, deltakerHistorikkService)
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
