package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.extensions.getForslagId
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.AvvisForslagRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import java.time.ZonedDateTime

fun Routing.registerVeilederApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    historikkService: DeltakerHistorikkService,
    deltakerResponseBuilder: DeltakerResponseBuilder,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    arrangorService: ArrangorService,
    forslagService: ForslagService,
    forslagRepository: ForslagRepository,
) {
    authenticate("SYSTEM") {
        get("/personident/deltaker/{deltakerId}") {
            val personident = deltakerRepository
                .getPersonidentForDeltaker(call.getDeltakerId())

            call.respond(PersonIdentResponse(personident))
        }

        get("/personident/forslag/{forslagId}") {
            val personident = deltakerRepository
                .getPersonidentForForslag(call.getForslagId())

            call.respond(PersonIdentResponse(personident))
        }

        post("/avvis-forslag/{forslagId}") {
            /*
             Avvis forslag kommer fra frontend som en egen type request, og ikke som en EndringRequest med forslagId (som godkjenning av forslag),
             fordi godkjenning av forslag har en Endring som skal iverksettes på deltaker(oppdatere, publisere), det er ikke tilfellet ved avvisning av forslag
             */
            val forslagId = call.getForslagId()
            val request = call.receive<AvvisForslagRequest>()
            val deltakerId = forslagRepository.get(forslagId).getOrThrow().deltakerId

            forslagService.avvisForslag(
                forslagId = forslagId,
                begrunnelse = request.begrunnelse,
                avvistAvAnsatt = request.avvistAvAnsatt,
                avvistAvEnhet = request.avvistAvEnhet,
            )

            val deltakerResponse = deltakerRepository
                .get(deltakerId)
                .getOrThrow()
                .let { deltakerResponseBuilder.buildDeltakerResponse(it) }

            call.respond(deltakerResponse)
        }

        route("/deltaker") {
            get("/{deltakerId}") {
                val deltakerResponse = deltakerRepository
                    .get(call.getDeltakerId())
                    .getOrThrow()
                    .let {
                        deltakerResponseBuilder.buildDeltakerResponse(it)
                    }

                call.respond(deltakerResponse)
            }

            post("/{deltakerId}/endre-deltaker") {
                val deltaker = deltakerService.upsertEndretDeltaker(
                    deltakerId = call.getDeltakerId(),
                    endringRequest = call.receive<EndringRequest>(),
                )
                call.respond(deltakerResponseBuilder.buildDeltakerResponse(deltaker))
            }

            get("/{deltakerId}/historikk") {
                val deltakerId = call.getDeltakerId()
                val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
                val historikk = historikkService.getForDeltaker(deltakerId)
                val ansatteIder = historikk.flatMap { it.navAnsatte() }.distinct().toSet()
                val enheterIder = historikk.flatMap { it.navEnheter() }.distinct().toSet()

                val response = DeltakerHistorikkDataResponse(
                    historikk = historikk,
                    arrangornavn = deltaker.deltakerliste.arrangor?.let { arrangor ->
                        arrangorService.getArrangorNavn(
                            arrangor = arrangor,
                            gjennomforingstype = deltaker.deltakerliste.gjennomforingstype,
                        )
                    } ?: "",
                    oppstartstype = deltaker.deltakerliste.oppstart,
                    pameldingstype = deltaker.deltakerliste.pameldingstype,
                    ansatte = navAnsattService.getMany(ansatteIder).associateBy { it.id },
                    enheter = navEnhetService
                        .getEnheter(enheterIder)
                        .values
                        .toList()
                        .associateBy { it.id },
                )
                call.respond(response)
            }

            post("/{deltakerId}/sist-besokt") {
                deltakerService.oppdaterSistBesokt(
                    deltakerId = call.getDeltakerId(),
                    sistBesokt = call.receive<ZonedDateTime>(),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
