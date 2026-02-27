package no.nav.amt.deltaker.bff.tiltakskoordinator.api

import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.deltaker.bff.deltaker.api.model.getArrangorNavn
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltakskoordinator.SporbarhetOgTilgangskontrollSvc
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

fun Routing.registerTiltakskoordinatorDeltakerApi(
    sporbarhetOgTilgangskontrollSvc: SporbarhetOgTilgangskontrollSvc,
    tiltakskoordinatorService: TiltakskoordinatorService,
    deltakerRepository: DeltakerRepository,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    ulesteHendelserService: UlestHendelseService,
) {
    val apiPath = "/tiltakskoordinator/deltaker/{id}"

    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        get(apiPath) {
            val deltakerId = UUID.fromString(call.parameters["id"])
            val tiltakskoordinatorsDeltaker = tiltakskoordinatorService.getDeltaker(deltakerId)

            val harTilgangTilBruker = sporbarhetOgTilgangskontrollSvc.kontrollerTilgangTilBruker(
                navIdent = call.getNavIdent(),
                navAnsattAzureId = call.getNavAnsattAzureId(),
                navBruker = tiltakskoordinatorsDeltaker.navBruker,
                deltakerlisteId = tiltakskoordinatorsDeltaker.deltakerliste.id,
            )

            val responseBody = tiltakskoordinatorsDeltaker.toResponse(
                harTilgangTilBruker,
                ulesteHendelserService.getUlesteHendelserForDeltaker(deltakerId),
            )

            call.respond(responseBody)
        }

        get("$apiPath/historikk") {
            val deltakerId = UUID.fromString(call.parameters["id"])
            val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

            sporbarhetOgTilgangskontrollSvc
                .kontrollerTilgangTilBruker(
                    navIdent = call.getNavIdent(),
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    navBruker = deltaker.navBruker,
                    deltakerlisteId = deltaker.deltakerliste.id,
                ).also { harTilgangTilBruker ->
                    if (!harTilgangTilBruker) {
                        throw AuthorizationException("Ansatt har ikke tilgang til å se historikken til deltaker $deltakerId")
                    }
                }

            val historikk = deltaker.getDeltakerHistorikkForVisning()

            val historikkResponse = historikk.toResponse(
                ansatte = navAnsattService.hentAnsatteForHistorikk(historikk),
                enheter = navEnhetService.hentEnheterForHistorikk(historikk),
                arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                oppstartstype = deltaker.deltakerliste.oppstart,
            )

            val historikkResponseAsJson = objectMapper.writePolymorphicListAsString(historikkResponse)

            call.respondText(historikkResponseAsJson, ContentType.Application.Json)
        }
    }
}
