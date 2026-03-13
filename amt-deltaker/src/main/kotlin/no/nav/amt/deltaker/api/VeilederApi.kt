package no.nav.amt.deltaker.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.deltakerEndringResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.deltaker.ResponseBuilder
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import java.time.ZonedDateTime

fun Routing.registerVeilederApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    historikkService: DeltakerHistorikkService,
    responseBuilder: ResponseBuilder,
) {
    authenticate("SYSTEM") {
        get("/deltaker/{deltakerId}") {
            val deltakerResponse = deltakerRepository
                .get(call.getDeltakerId())
                .onFailure { call.respond(HttpStatusCode.NotFound) }
                .getOrThrow()
                .let { responseBuilder.buildDeltakerResponse(it) }

            call.respond(deltakerResponse)
        }

        post("/deltaker/{deltakerId}/endre-deltaker") {
            val deltaker = deltakerService.upsertEndretDeltaker(
                deltakerId = call.getDeltakerId(),
                endringRequest = call.receive<EndringRequest>(),
            )
            val historikk = historikkService.getForDeltaker(deltaker.id)

            call.respond(deltakerEndringResponseFromDeltaker(deltaker, historikk))
        }

        get("/deltaker/{deltakerId}/historikk") {
            val historikk = historikkService.getForDeltaker(call.getDeltakerId())
            val historikkResponse = objectMapper.writePolymorphicListAsString(historikk)
            call.respondText(historikkResponse, ContentType.Application.Json)
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
