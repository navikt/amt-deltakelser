package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest

fun Routing.registerUtkastApi() {
    authenticate("VEILEDER") {
        /*
            Oppretter utkast for en enkeltplass deltaker.
            Opprettes i handlingen "Del utkast"
            Status: Kladd/utkast -> Utkast
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/enkeltplass-utkast/{deltakerId}") {
            // val request = call.receive<EnkeltplassUtkastRequest>()
            throw NotImplementedError("Dette er ikke implementert.")
        }

        /*
           Direktepåmelding av enkeltplass  deltaker uten at utkast/deltakelsen er delt med innbygger
           Handling: "Meld på uten å dele utkast"
           Status Kladd/Utkast -> Venter på oppstart/søkt inn
           @Returns no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/enkeltplass-utkast/{deltakerId}/meld-paa-direkte") {
            // val request = call.receive<EnkeltplassUtkastRequest>()

            throw NotImplementedError("Dette er ikke implementert.")
        }

        /*
            Oppretter/endrer utkast for en deltaker.
            Handling: "Del utkast"
            Status: Kladd/utkast -> Utkast
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/utkast/{deltakerId}") {
            val request = call.receive<UtkastRequest>()
            // Skal pastes kode fra /pamelding/{deltakerId}
            // Men responsen skal komme fra amt-deltaker
            throw NotImplementedError("Dette er ikke implementert.")
            // Return DeltakerResponse (tidligere  komplettdeltakerresponse())
        }

        /*
            Direktepåmelding av deltaker uten at utkast/deltakelsen er delt med innbygger
            Handling: "Meld på uten å dele utkast"
            Status Kladd/Utkast -> Venter på oppstart/søkt inn
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/utkast/{deltakerId}/meld-paa-direkte") {
            // Erstatter post("/pamelding/{deltakerId}/utenGodkjenning")
            // henter data fra amt-deltaker
            val request = call.receive<PameldingUtenGodkjenningRequest>()

            throw NotImplementedError("Dette er ikke implementert.")
        }

        /*
            Avbryter utkast
            Handling:
            Status Utkast -> Avbrutt utkast
         */
        post("/utkast/{deltakerId}/avbryt") {
            // Erstatter post("/pamelding/{deltakerId}/avbryt")
            // henter data fra amt-deltaker
            throw NotImplementedError("Dette er ikke implementert.")
        }
    }
}
