package no.nav.amt.deltaker.bff.application.plugins

import io.getunleash.Unleash
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.EnkeltplassClient
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.clients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.enkeltplass.registerEnkeltplassApi
import no.nav.amt.deltaker.bff.enkeltplass.validate
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteStengtException
import no.nav.amt.deltaker.bff.innbygger.api.registerInnbyggerApi
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.registerTiltakskoordinatorDeltakerApi
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.registerTiltakskoordinatorDeltakerlisteApi
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.registerUlestHendelseApi
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseBuilder
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.veileder.api.registerArrangorsokApi
import no.nav.amt.deltaker.bff.veileder.api.registerKladdApi
import no.nav.amt.deltaker.bff.veileder.api.registerPameldingApi
import no.nav.amt.deltaker.bff.veileder.api.registerUnleashApi
import no.nav.amt.deltaker.bff.veileder.api.registerVeilederApi
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringClient
import no.nav.amt.lib.ktor.routing.registerHealthApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<EnkeltplassPameldingRequest> { request -> request.validate() }
    }
}

fun Application.configureRouting(
    tilgangskontrollService: TilgangskontrollService,
    selfServiceTilgangService: SelfServiceTilgangService,
    deltakerService: DeltakerService,
    pameldingService: PameldingService,
    paameldingClient: PaameldingClient,
    navAnsattService: NavAnsattService,
    forslagRepository: ForslagRepository,
    amtDistribusjonClient: AmtDistribusjonClient,
    amtDeltakerClient: AmtDeltakerClient,
    arrangorsokClient: ArrangorsokClient,
    enkeltplassClient: EnkeltplassClient,
    sporbarhetsloggService: SporbarhetsloggService,
    deltakerlisteService: DeltakerlisteService,
    deltakerlisteRepository: DeltakerlisteRepository,
    unleash: Unleash,
    tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService,
    tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository,
    opplaringKategoriseringClient: OpplaringKategoriseringClient,
    tiltakskoordinatorResponseBuilder: ResponseBuilder,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
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
        exception<DeltakerlisteStengtException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.Gone, call, cause)
            call.respondText(text = "410: ${cause.message}", status = HttpStatusCode.Gone)
        }
        exception<Throwable> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.InternalServerError, call, cause)
            call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        registerHealthApi()

        registerEnkeltplassApi(
            amtDeltakerClient = amtDeltakerClient,
            tilgangskontrollService = tilgangskontrollService,
            enkeltplassClient = enkeltplassClient,
            opplaringKategoriseringClient = opplaringKategoriseringClient,
        )

        registerVeilederApi(
            tilgangskontrollService = tilgangskontrollService,
            forslagRepository = forslagRepository,
            amtDeltakerClient = amtDeltakerClient,
            sporbarhetsloggService = sporbarhetsloggService,
        )

        registerPameldingApi(
            tilgangskontrollService = tilgangskontrollService,
            amtDistribusjonClient = amtDistribusjonClient,
            amtDeltakerClient = amtDeltakerClient,
            pameldingClient = paameldingClient,
        )

        registerKladdApi(
            tilgangskontrollService = tilgangskontrollService,
            amtDeltakerClient = amtDeltakerClient,
            paameldingClient = paameldingClient,
            paameldingService = pameldingService,
        )

        registerInnbyggerApi(
            deltakerService = deltakerService,
            amtDeltakerClient = amtDeltakerClient,
            tilgangskontrollService = tilgangskontrollService,
            pameldingClient = paameldingClient,
        )

        registerUnleashApi(unleash)

        registerTiltakskoordinatorDeltakerApi(
            tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
            amtDeltakerClient = amtDeltakerClient,
            tiltakskoordinatorClient = tiltakskoordinatorClient,
        )

        registerTiltakskoordinatorDeltakerlisteApi(
            deltakerlisteService = deltakerlisteService,
            tiltakskoordinatorTilgangRepository = tiltakskoordinatorTilgangRepository,
            navAnsattService = navAnsattService,
            tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
            selfServiceTilgang = selfServiceTilgangService,
            tiltakskoordinatorClient = tiltakskoordinatorClient,
            responseBuilder = tiltakskoordinatorResponseBuilder,
            deltakerlisteRepository = deltakerlisteRepository,
        )

        registerUlestHendelseApi(tiltakskoordinatorClient)

        registerArrangorsokApi(arrangorsokClient = arrangorsokClient)

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
