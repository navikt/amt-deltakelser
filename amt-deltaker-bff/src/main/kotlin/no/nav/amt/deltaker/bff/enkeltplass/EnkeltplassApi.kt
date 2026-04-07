package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.apiclients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.EnkeltplassClient
import no.nav.amt.deltaker.bff.apiclients.ModelMapper
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettEnkeltplassKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest

fun Routing.registerEnkeltplassApi(
    amtDeltakerClient: AmtDeltakerClient,
    enkeltplassClient: EnkeltplassClient,
    tilgangskontrollService: TilgangskontrollService,
) {
    authenticate("VEILEDER") {
        route("/enkeltplass") {
            /*
            Oppretter kladd for en enkeltplass deltaker.
            Opprettes automatisk når man trykker seg inn i påmeldingsskjemaet
            Status: Kladd
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/opprett-kladd") {
                val request = call.receive<OpprettEnkeltplassKladdRequest>()

                tilgangskontrollService.verifiserSkrivetilgang(call.getNavAnsattAzureId(), request.personident)

                val response = enkeltplassClient
                    .opprettKladdEnkeltplass(request.tiltakskode, request.personident)
                    .let { amtDeltakerClient.getDeltaker(it.deltakerId) }
                    .let { ModelMapper.toDeltaker(it) }
                    .let { DeltakerResponse.fromDeltakerModel(it) }

                call.respond(response)
            }

            /*
           Oppdaterer kladd for en enkeltplass deltaker.
           Endepunktet kalles automatisk når veileder trykker seg bort fra et inputfelt i skjaet
           Status: Kladd
           @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/oppdater-kladd/{deltakerId}") {
                val deltakerId = call.getDeltakerId()
                val request = call.receive<OppdaterEnkeltplassKladdRequest>()
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId).personident
                tilgangskontrollService.verifiserSkrivetilgang(call.getNavAnsattAzureId(), personident)

                val response = enkeltplassClient
                    .oppdaterKladdEnkeltplass(deltakerId, request)
                    .let { amtDeltakerClient.getDeltaker(deltakerId) }
                    .let { ModelMapper.toDeltaker(it) }
                    .let { DeltakerResponse.fromDeltakerModel(it) }

                call.respond(response)
            }

            /*
            Oppretter utkast for en enkeltplass deltaker.
            Opprettes i handlingen "Del utkast"
            Status: Kladd/utkast -> Utkast
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/utkast/{deltakerId}") {
                // val request = call.receive<EnkeltplassUtkastRequest>()
                throw NotImplementedError("Dette er ikke implementert.")
            }

            /*
           Direktepåmelding av enkeltplass  deltaker uten at utkast/deltakelsen er delt med innbygger
           Handling: "Meld på uten å dele utkast"
           Status Kladd/Utkast -> søkt inn
           @Returns no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/utkast/{deltakerId}/meld-paa-direkte") {
                // tilsvarer post("/pamelding/{deltakerId}/utenGodkjenning") for enkeltplasser

                val deltakerId = call.getDeltakerId()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = amtDeltakerClient.getPersonidentForDeltaker(deltakerId).personident,
                )

                val request: EnkeltplassPameldingRequest = call.receive()

                enkeltplassClient.meldPaaDirekte(
                    deltakerId = deltakerId,
                    EnkeltplassPameldingDecoratedRequest(
                        wrappedRequest = request,
                        endretAvEnhet = call.getEnhetsnummer(),
                        endretAv = call.getNavIdent(),
                    ),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
