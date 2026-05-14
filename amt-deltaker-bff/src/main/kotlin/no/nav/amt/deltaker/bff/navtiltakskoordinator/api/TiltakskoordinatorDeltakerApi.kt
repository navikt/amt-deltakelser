package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.amt.lib.utils.writePolymorphicListAsString
import java.util.UUID

fun Routing.registerTiltakskoordinatorDeltakerApi(
    tiltakskoordinatorTilgangskontrollService: TiltakskoordinatorTilgangskontrollService,
    tiltakskoordinatorService: TiltakskoordinatorService,
    deltakerRepository: DeltakerRepository,
    amtDeltakerClient: AmtDeltakerClient,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    ulesteHendelserService: UlestHendelseService,
    unleashToggle: CommonUnleashToggle,
) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        route("/tiltakskoordinator/deltaker/{id}") {
            get {
                val deltakerId = UUID.fromString(call.parameters["id"])

                val responseBody = if (unleashToggle.prioriterSynkronKommunikasjon()) {
                    val deltaker = amtDeltakerClient
                        .getDeltaker(deltakerId)
                        .let { ModelMapper.toDeltaker(it) }

                    val harTilgangTilBruker = tiltakskoordinatorTilgangskontrollService.kontrollerTilgangTilBruker(
                        navIdent = call.getNavIdent(),
                        navAnsattAzureId = call.getNavAnsattAzureId(),
                        personident = deltaker.navBruker.personident,
                        erSkjermet = deltaker.navBruker.erSkjermet,
                        adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                        deltakerlisteId = deltaker.gjennomforing.id,
                    )

                    val ulesteHendelser = ulesteHendelserService.getUlesteHendelserForDeltaker(deltakerId)

                    deltaker.let {
                        ResponseMapper.buildDeltakerDetaljerResponse(
                            deltaker = it,
                            tilgangTilBruker = harTilgangTilBruker,
                            ulesteHendelser = ulesteHendelser,
                        )
                    }
                } else {
                    // Skal slettes etter hvert
                    val tiltakskoordinatorsDeltaker = tiltakskoordinatorService.getDeltaker(deltakerId)
                    val harTilgangTilBruker = tiltakskoordinatorTilgangskontrollService.kontrollerTilgangTilBruker(
                        navIdent = call.getNavIdent(),
                        navAnsattAzureId = call.getNavAnsattAzureId(),
                        personident = tiltakskoordinatorsDeltaker.navBruker.personident,
                        erSkjermet = tiltakskoordinatorsDeltaker.navBruker.erSkjermet,
                        adressebeskyttelse = tiltakskoordinatorsDeltaker.navBruker.adressebeskyttelse,
                        deltakerlisteId = tiltakskoordinatorsDeltaker.deltakerliste.id,
                    )
                    val ulesteHendelser = ulesteHendelserService.getUlesteHendelserForDeltaker(deltakerId)

                    tiltakskoordinatorsDeltaker.toResponse(
                        harTilgangTilBruker = harTilgangTilBruker,
                        ulesteHendelser = ulesteHendelser,
                    )
                }

                call.respond(responseBody)
            }

            get("/historikk") {
                val deltakerId = UUID.fromString(call.parameters["id"])

                val historikkResponse = if (unleashToggle.prioriterSynkronKommunikasjon()) {
                    val deltakerResponse = amtDeltakerClient.getDeltaker(deltakerId)
                    tiltakskoordinatorTilgangskontrollService
                        .kontrollerTilgangTilBruker(
                            navIdent = call.getNavIdent(),
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            personident = deltakerResponse.navBruker.personident,
                            erSkjermet = deltakerResponse.navBruker.erSkjermet,
                            adressebeskyttelse = deltakerResponse.navBruker.adressebeskyttelse,
                            deltakerlisteId = deltakerResponse.gjennomforing.id,
                        ).also { harTilgangTilBruker ->
                            if (!harTilgangTilBruker) {
                                throw AuthorizationException("Ansatt har ikke tilgang til å se historikken til deltaker $deltakerId")
                            }
                        }
                    val data = amtDeltakerClient.getDeltakerHistorikkData(deltakerId)
                    DeltakerHistorikkResponse.fromModels(
                        models = data.historikk,
                        arrangornavn = data.arrangornavn,
                        oppstartstype = data.oppstartstype,
                        pameldingstype = deltakerResponse.gjennomforing.pameldingstype,
                        enheter = data.enheter,
                        ansatte = data.ansatte,
                    )
                } else {
                    val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
                    tiltakskoordinatorTilgangskontrollService
                        .kontrollerTilgangTilBruker(
                            navIdent = call.getNavIdent(),
                            navAnsattAzureId = call.getNavAnsattAzureId(),
                            personident = deltaker.navBruker.personident,
                            erSkjermet = deltaker.navBruker.erSkjermet,
                            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
                            deltakerlisteId = deltaker.deltakerliste.id,
                        ).also { harTilgangTilBruker ->
                            if (!harTilgangTilBruker) {
                                throw AuthorizationException("Ansatt har ikke tilgang til å se historikken til deltaker $deltakerId")
                            }
                        }
                    val historikk = deltaker.getDeltakerHistorikkForVisning()
                    DeltakerHistorikkResponse.fromModels(
                        models = historikk,
                        arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                        oppstartstype = deltaker.deltakerliste.oppstart,
                        pameldingstype = deltaker.deltakerliste.pameldingstype,
                        enheter = navEnhetService.hentEnheterForHistorikk(historikk),
                        ansatte = navAnsattService.hentAnsatteForHistorikk(historikk),
                    )
                }

                val historikkResponseAsJson = objectMapper.writePolymorphicListAsString(historikkResponse)

                call.respondText(historikkResponseAsJson, ContentType.Application.Json)
            }
        }
    }
}
