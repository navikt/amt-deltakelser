package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.extensions.getDeltakerId

fun Routing.registerEnkeltplassApi(
    deltakerRepository: DeltakerRepository,
    tilgangskontrollService: TilgangskontrollService,
    enkeltplassManager: EnkeltplassManager,
) {
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
           Status Kladd/Utkast -> søkt inn
           @Returns no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/enkeltplass-utkast/{deltakerId}/meld-paa-direkte") {
            // tilsvarer post("/pamelding/{deltakerId}/utenGodkjenning") for enkeltplasser
            // val request = call.receive<EnkeltplassUtkastRequest>()
            // Requeste gjennomføring hos valp(via amt-deltaker)

            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            enkeltplassManager.meldPaaDirekte(deltaker)

            call.respond(HttpStatusCode.OK)
        }
    }
}
