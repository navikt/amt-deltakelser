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
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseBuilder
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper.toDeltakerResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.internapi.deltaker.request.PageRequest
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.util.UUID

fun Routing.registerTiltakskoordinatorDeltakerlisteApi(
    deltakerlisteService: DeltakerlisteService,
    tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService,
    selfServiceTilgang: SelfServiceTilgangService,
    tiltakskoordinatorService: TiltakskoordinatorService,
    tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository,
    navAnsattService: NavAnsattService,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
    responseBuilder: ResponseBuilder,
) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        route("/tiltakskoordinator/deltakerliste/{id}") {
            get {
                val deltakerlisteId = getDeltakerlisteId()
                val paaloggetNavAnsatt = navAnsattService.hentNavAnsatt(call.getNavIdent())

                // Bør nav ansatt bli igjen i bff database?
                val koordinatorer = tiltakskoordinatorTilgangRepository.hentKoordinatorer(
                    deltakerlisteId = deltakerlisteId,
                    paaloggetNavAnsattId = paaloggetNavAnsatt.id,
                )

                val gjennomforingResponse = tiltakskoordinatorClient
                    .getGjennomforing(deltakerlisteId)
                    .let {
                        ResponseMapper.buildGjennomforing(
                            gjennomforingResponse = it,
                            koordinatortilganger = koordinatorer,
                        )
                    }

                call.respond(gjennomforingResponse)
            }

            // TODO: skal fjernes
            get("/deltakere") {
                val deltakerlisteId = getDeltakerlisteId()
                val pagedResponse = hentDeltakereForDeltakerliste(
                    deltakerlisteId = deltakerlisteId,
                    request = TiltaksKoordinatorDeltakerlisteRequest(
                        gjennomforingId = deltakerlisteId,
                        pageRequest = PageRequest(pageSize = 5500),
                    ),
                    deltakerlisteService = deltakerlisteService,
                    selfServiceTilgang = selfServiceTilgang,
                    tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
                    tiltakskoordinatorClient = tiltakskoordinatorClient,
                    responseBuilder = responseBuilder,
                )

                call.respond(pagedResponse.data)
            }

            post("/deltakere-paged") {
                val deltakerlisteId = getDeltakerlisteId()
                val request = call.receive<TiltaksKoordinatorDeltakerlisteRequest>()

                require(request.gjennomforingId == deltakerlisteId) {
                    "DeltakerlisteId i request må matche URL parameter."
                }

                val response = hentDeltakereForDeltakerliste(
                    deltakerlisteId = deltakerlisteId,
                    request = request,
                    deltakerlisteService = deltakerlisteService,
                    selfServiceTilgang = selfServiceTilgang,
                    tiltakskoordinatorTilgangskontrollService = tiltakskoordinatorTilgangskontrollService,
                    tiltakskoordinatorClient = tiltakskoordinatorClient,
                    responseBuilder = responseBuilder,
                )

                call.respond(response)
            }

            post("/deltakere/tildel-plass") {
                val navIdent = call.getNavIdent()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(
                    deltakerIder = deltakerIder,
                    deltakerlisteId = getDeltakerlisteId(),
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorService.endreDeltakere(
                    deltakerIder = deltakerIder,
                    endring = EndringFraTiltakskoordinator.TildelPlass,
                    endretAv = navIdent,
                )

                val deltakereResponse = oppdaterteDeltakere
                    .map { deltaker ->
                        deltaker.toDeltakerResponse(
                            kanSeInnbyggersNavn = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                                navAnsattAzureId = call.getNavAnsattAzureId(),
                                erSkjermet = deltaker.navBruker.erSkjermet,
                                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                            ),
                        )
                    }

                call.respond(deltakereResponse)
            }

            post("/deltakere/sett-paa-venteliste") {
                val navIdent = call.getNavIdent()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(
                    deltakerIder = deltakerIder,
                    deltakerlisteId = getDeltakerlisteId(),
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorService.endreDeltakere(
                    deltakerIder = deltakerIder,
                    endring = EndringFraTiltakskoordinator.SettPaaVenteliste,
                    endretAv = navIdent,
                )

                val deltakereResponse = oppdaterteDeltakere.map { deltaker ->
                    deltaker.toDeltakerResponse(
                        kanSeInnbyggersNavn = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        ),
                    )
                }

                call.respond(deltakereResponse)
            }

            post("/deltakere/del-med-arrangor") {
                val navIdent = call.getNavIdent()
                val deltakerIder = call.receive<List<UUID>>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(
                    deltakerIder = deltakerIder,
                    deltakerlisteId = getDeltakerlisteId(),
                    navIdent = navIdent,
                )

                val oppdaterteDeltakere = tiltakskoordinatorService
                    .endreDeltakere(
                        deltakerIder = deltakerIder,
                        endring = EndringFraTiltakskoordinator.DelMedArrangor,
                        endretAv = navIdent,
                    ).map { deltaker ->
                        deltaker.toDeltakerResponse(
                            kanSeInnbyggersNavn = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                                navAnsattAzureId = call.getNavAnsattAzureId(),
                                erSkjermet = deltaker.navBruker.erSkjermet,
                                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                            ),
                        )
                    }

                call.respond(oppdaterteDeltakere)
            }

            post("/deltakere/gi-avslag") {
                val navIdent = call.getNavIdent()
                val request = call.receive<AvslagRequest>()

                tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(
                    deltakerIder = listOf(request.deltakerId),
                    deltakerlisteId = getDeltakerlisteId(),
                    navIdent = navIdent,
                )

                val oppdatertDeltaker = tiltakskoordinatorService.giAvslag(
                    request = request,
                    endretAv = navIdent,
                )

                val harTilgang = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    erSkjermet = oppdatertDeltaker.navBruker.erSkjermet,
                    adressebeskyttelse = oppdatertDeltaker.navBruker.adressebeskyttelse,
                )

                call.respond(oppdatertDeltaker.toDeltakerResponse(harTilgang))
            }

            post("/tilgang/legg-til") {
                selfServiceTilgang
                    .leggTilTiltakskoordinatorTilgang(
                        navIdent = call.getNavIdent(),
                        deltakerlisteId = getDeltakerlisteId(),
                    ).getOrThrow()

                call.respond(HttpStatusCode.OK)
            }

            post("/tilgang/fjern") {
                selfServiceTilgang
                    .fjernTiltakskoordinatorTilgang(
                        call.getNavIdent(),
                        getDeltakerlisteId(),
                    ).getOrThrow()

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun RoutingContext.getDeltakerlisteId(): UUID {
    val id =
        call.parameters["id"] ?: throw IllegalArgumentException("Påkrevd URL parameter 'deltakerlisteId' mangler.")

    return try {
        UUID.fromString(id)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("URL parameter 'deltakerlisteId' er ikke formattert riktig.")
    }
}

private suspend fun RoutingContext.hentDeltakereForDeltakerliste(
    deltakerlisteId: UUID,
    request: TiltaksKoordinatorDeltakerlisteRequest,
    deltakerlisteService: DeltakerlisteService,
    selfServiceTilgang: SelfServiceTilgangService,
    tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
    responseBuilder: ResponseBuilder,
): PaginatedResult<DeltakerResponse> {
    deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)
    selfServiceTilgang.verifiserTiltakskoordinatorTilgang(
        navIdent = call.getNavIdent(),
        deltakerlisteId = deltakerlisteId,
    )

    val response = tiltakskoordinatorClient.getDeltakereForGjennomforing(request)
    val navAnsattAzureId = call.getNavAnsattAzureId()
    val deltakere = responseBuilder.toDeltakerResponses(
        deltakere = response.paginatedResult.data,
        kanSeInnbyggersNavn = { deltaker ->
            tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId = navAnsattAzureId,
                erSkjermet = deltaker.navBruker.erSkjermet,
                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
            )
        },
    )

    return PaginatedResult(
        totalCount = response.paginatedResult.totalCount,
        pageSize = response.paginatedResult.pageSize,
        data = deltakere,
    )
}
