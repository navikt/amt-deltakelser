package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.api.response.ResponseMapper
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import java.time.ZonedDateTime

fun Routing.registerVeilederApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    historikkService: DeltakerHistorikkService,
    responseBuilder: ResponseBuilder,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    arrangorService: ArrangorService,
) {
    authenticate("SYSTEM") {
        get("/personident/{deltakerId}") {
            val personident = deltakerRepository
                .getPersonidentForDeltaker(call.getDeltakerId())

            call.respond(PersonIdentResponse(personident))
        }

        get("/deltaker/{deltakerId}") {
            val deltakerResponse = deltakerRepository
                .get(call.getDeltakerId())
                .getOrThrow()
                .let {
                    responseBuilder.buildDeltakerResponse(
                        deltaker = it,
                        includeKodeverk = true,
                    )
                }

            call.respond(deltakerResponse)
        }

        post("/deltaker/{deltakerId}/endre-deltaker") {
            val deltaker = deltakerService.upsertEndretDeltaker(
                deltakerId = call.getDeltakerId(),
                endringRequest = call.receive<EndringRequest>(),
            )
            val historikk = historikkService.getForDeltaker(deltaker.id)

            call.respond(ResponseMapper.deltakerEndringResponseFromDeltaker(deltaker, historikk))
        }

        get("/deltaker/{deltakerId}/historikk") {
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

        post("/deltaker/{deltakerId}/sist-besokt") {
            deltakerService.oppdaterSistBesokt(
                deltakerId = call.getDeltakerId(),
                sistBesokt = call.receive<ZonedDateTime>(),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}
