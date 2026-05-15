package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.api.response.SharedResponseMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.internapi.paamelding.request.KladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun Routing.registerKladdApi(
    kladdService: KladdService,
    deltakerRepository: DeltakerRepository,
) {
    authenticate("SYSTEM") {
        post("/kladd") {
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val deltaker = kladdService.opprettKladd(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(opprettKladdResponseFromDeltaker(deltaker))
        }

        post("/oppdater-kladd/{deltakerId}") {
            val kladdRequest = call.receive<KladdRequest>()
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
                "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id ${deltaker.id} har status ${deltaker.status.type}"
            }
            kladdService.oppdaterKladd(
                deltaker = deltaker,
                innhold = kladdRequest.innhold.toInnholdModel(deltaker),
                bakgrunnsinformasjon = kladdRequest.bakgrunnsinformasjon,
                deltakelsesprosent = kladdRequest.deltakelsesprosent,
                dagerPerUke = kladdRequest.dagerPerUke,
            )

            call.respond(HttpStatusCode.OK)
        }

        delete("/kladd/{deltakerId}") {
            kladdService.slettKladd(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }
    }
}
