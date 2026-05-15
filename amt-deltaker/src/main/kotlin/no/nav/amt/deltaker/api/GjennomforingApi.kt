package no.nav.amt.deltaker.api

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.extensions.getGjennomforingId
import no.nav.amt.deltaker.repository.DeltakerlisteRepository

fun Routing.registerGjennomforingApi(
    deltakerlisteRepository: DeltakerlisteRepository,
    deltakerResponseBuilder: DeltakerResponseBuilder,
) {
    authenticate("SYSTEM") {
        get("/gjennomforing/{gjennomforingId}") {
            val gjennomforingId = call.getGjennomforingId()
            val gjennomforingResponse = deltakerlisteRepository
                .get(gjennomforingId)
                .getOrThrow()
                .let {
                    deltakerResponseBuilder.buildGjennomforingResponse(
                        deltakerliste = it,
                        includeKodeverk = false,
                    )
                }

            call.respond(gjennomforingResponse)
        }
    }
}
