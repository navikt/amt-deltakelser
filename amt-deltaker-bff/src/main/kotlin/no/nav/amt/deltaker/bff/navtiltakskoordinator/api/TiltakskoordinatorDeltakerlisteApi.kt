package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseBuilder
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import java.util.UUID

fun Routing.registerTiltakskoordinatorDeltakerlisteApi(
    deltakerlisteService: DeltakerlisteService,
    deltakerlisteRepository: DeltakerlisteRepository,
    tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService,
    selfServiceTilgang: SelfServiceTilgangService,
    tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository,
    navAnsattService: NavAnsattService,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
    responseBuilder: ResponseBuilder,
) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        route("/tiltakskoordinator/deltakerliste/{id}") {
            get {
                val gjennomforingId = getGjennomforingId()
                val paaloggetNavAnsatt = navAnsattService.hentNavAnsatt(call.getNavIdent())

                // Bør nav ansatt bli igjen i bff database?
                val koordinatorer = tiltakskoordinatorTilgangRepository.hentKoordinatorer(
                    deltakerlisteId = gjennomforingId,
                    paaloggetNavAnsattId = paaloggetNavAnsatt.id,
                )

                val gjennomforingResponse = tiltakskoordinatorClient
                    .getGjennomforing(gjennomforingId)
                    .let {
                        ResponseMapper.buildGjennomforing(
                            gjennomforingResponse = it,
                            koordinatortilganger = koordinatorer,
                        )
                    }

                call.respond(gjennomforingResponse)
            }

            post("/deltakere") {
                val gjennomforingId = getGjennomforingId()

                deltakerlisteService.verifiserTilgjengeligDeltakerliste(gjennomforingId)
                selfServiceTilgang.verifiserTiltakskoordinatorTilgang(
                    navIdent = call.getNavIdent(),
                    deltakerlisteId = gjennomforingId,
                )

                val request = call
                    .receive<TiltaksKoordinatorDeltakerlisteRequest>()
                    .copy(gjennomforingId = gjennomforingId)

                val deltakerResponses = tiltakskoordinatorClient
                    .getDeltakereForGjennomforing(request)
                    .data

                val navAnsattAzureId = call.getNavAnsattAzureId()

                val deltakere = responseBuilder
                    .toDeltakereResponse(
                        deltakere = deltakerResponses,
                        kanSeInnbyggersNavn = { deltaker ->
                            tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                                navAnsattAzureId = navAnsattAzureId,
                                erSkjermet = deltaker.navBruker.erSkjermet,
                                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                            )
                        },
                    ).filter { deltaker -> deltaker.matchesHandlingFilter(request.handlingFilterValg) }

                call.respond(deltakere)
            }

            post("/deltakere/status-counts") {
                val gjennomforingId = getGjennomforingId()

                deltakerlisteService.verifiserTilgjengeligDeltakerliste(gjennomforingId)
                selfServiceTilgang.verifiserTiltakskoordinatorTilgang(
                    navIdent = call.getNavIdent(),
                    deltakerlisteId = gjennomforingId,
                )

                val request = call
                    .receive<TiltaksKoordinatorDeltakerlisteRequest>()
                    .copy(gjennomforingId = gjennomforingId)

                require(request.statuser.isNotEmpty()) {
                    "Statuser må spesifiseres for å hente deltakerantall per status"
                }

                call.respond(deltakerlisteRepository.getDeltakereCountPerStatus(request))
            }

            post("/deltakere/tildel-plass") {
                val navIdent = call.getNavIdent()
                val gjennomforingId = getGjennomforingId()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(
                    gjennomforingId = gjennomforingId,
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorClient
                    .tildelPlass(
                        gjennomforingId = gjennomforingId,
                        deltakerIder = deltakerIder,
                        endretAv = navIdent,
                    )
                val response = responseBuilder.toDeltakereResponse(
                    deltakere = oppdaterteDeltakere.map { it.deltaker },
                    kanSeInnbyggersNavn = { deltaker ->
                        tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        )
                    },
                )

                call.respond(response)
            }

            post("/deltakere/sett-paa-venteliste") {
                val navIdent = call.getNavIdent()
                val gjennomforingId = getGjennomforingId()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(
                    gjennomforingId = gjennomforingId,
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorClient.settPaaVenteliste(
                    gjennomforingId = gjennomforingId,
                    deltakerIder = deltakerIder,
                    endretAv = navIdent,
                )

                val response = responseBuilder.toOppdatertDeltakerResponse(
                    deltakere = oppdaterteDeltakere,
                    kanSeInnbyggersNavn = { deltaker ->
                        tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        )
                    },
                )

                call.respond(response)
            }

            post("/deltakere/del-med-arrangor") {
                val navIdent = call.getNavIdent()
                val gjennomforingId = getGjennomforingId()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(
                    gjennomforingId = gjennomforingId,
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorClient.delMedArrangor(gjennomforingId, deltakerIder, navIdent)

                val response = responseBuilder.toOppdatertDeltakerResponse(
                    deltakere = oppdaterteDeltakere,
                    kanSeInnbyggersNavn = { deltaker ->
                        tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        )
                    },
                )

                call.respond(response)
            }

            post("/deltakere/gi-avslag") {
                val navIdent = call.getNavIdent()
                val gjennomforingId = getGjennomforingId()
                val request = call.receive<AvslagRequest>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(
                    gjennomforingId = gjennomforingId,
                    navIdent = navIdent,
                )

                val deltakerOppdatering = tiltakskoordinatorClient.giAvslag(
                    gjennomforingId = gjennomforingId,
                    avslagRequest = request,
                    endretAv = navIdent,
                )

                val response = responseBuilder.toOppdatertDeltakerResponse(
                    deltakere = listOf(deltakerOppdatering),
                    kanSeInnbyggersNavn = { deltaker ->
                        tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        )
                    },
                )

                call.respond(response)
            }

            post("/tilgang/legg-til") {
                selfServiceTilgang
                    .leggTilTiltakskoordinatorTilgang(
                        navIdent = call.getNavIdent(),
                        deltakerlisteId = getGjennomforingId(),
                    ).getOrThrow()

                call.respond(HttpStatusCode.OK)
            }

            post("/tilgang/fjern") {
                selfServiceTilgang
                    .fjernTiltakskoordinatorTilgang(
                        call.getNavIdent(),
                        getGjennomforingId(),
                    ).getOrThrow()

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun RoutingContext.getGjennomforingId(): UUID {
    val id = call.parameters["id"] ?: throw IllegalArgumentException("Påkrevd URL parameter 'id'(gjennomføringId) mangler.")

    return try {
        UUID.fromString(id)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("URL parameter 'id'(gjennomføringId) er ikke formattert riktig.")
    }
}
