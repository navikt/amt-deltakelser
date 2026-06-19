package no.nav.amt.deltaker.bff.enkeltplass

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.EnkeltplassClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.extensions.getTerm
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettEnkeltplassKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient

fun Routing.registerEnkeltplassApi(
    amtDeltakerClient: AmtDeltakerClient,
    enkeltplassClient: EnkeltplassClient,
    tilgangskontrollService: TilgangskontrollService,
    kodeverkClient: KodeverkClient,
) {
    authenticate(AuthLevel.VEILEDER.name) {
        route("/enkeltplass") {
            get("/kodeverk-sertifiseringer/sok/{term}") {
                val sertifiseringer = kodeverkClient.sertifiseringSok(call.getTerm())
                call.respond(sertifiseringer)
            }

            get("/kodeverk/{deltakerId}") {
                val deltakerId = call.getDeltakerId()
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

                tilgangskontrollService.verifiserLesetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = personident,
                )

                val gjennomforing = amtDeltakerClient.getDeltaker(deltakerId).gjennomforing
                val kodeverk = kodeverkClient.hentKodeverk(gjennomforing.tiltakstype.tiltakskode)

                call.respond(kodeverk.settValgt(gjennomforing.utflatetKodeverk))
            }

            /*
            Oppretter kladd for en enkeltplass deltaker.
            Opprettes automatisk når man trykker seg inn i påmeldingsskjemaet
            Status: Kladd
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/opprett-kladd") {
                val request = call.receive<OpprettEnkeltplassKladdRequest>()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = request.personident,
                )

                val response = enkeltplassClient
                    .opprettKladd(request.tiltakskode, request.personident)
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
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

                val oppdaterEnkeltplassKladdRequest = call.receive<OppdaterEnkeltplassKladdRequest>()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = personident,
                )

                enkeltplassClient.oppdaterKladd(
                    deltakerId = deltakerId,
                    kladdRequest = oppdaterEnkeltplassKladdRequest.sanitized(),
                )

                call.respond(HttpStatusCode.OK)
            }

            /*
            Oppdaterer eksisterende utkast for en enkeltplass deltaker.
            Opprettes i handlingen "Del oppdatert utkast"
            Status: Utkast -> Utkast
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/utkast/{deltakerId}") {
                val deltakerId = call.getDeltakerId()
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

                val pameldingRequest = call.receive<EnkeltplassPameldingRequest>()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = personident,
                )

                val deltakerResponse = enkeltplassClient
                    .oppdaterUtkast(
                        deltakerId = deltakerId,
                        pameldingDecoratedRequest = EnkeltplassPameldingDecoratedRequest(
                            wrappedRequest = pameldingRequest.sanitized(),
                            endretAvEnhet = call.getEnhetsnummer(),
                            endretAv = call.getNavIdent(),
                        ),
                    ).let { ModelMapper.toDeltaker(it) }
                    .let { deltakerModel -> DeltakerResponse.fromDeltakerModel(deltakerModel) }

                call.respond(deltakerResponse)
            }

            /*
           Oppretter utkast for en enkeltplass deltaker.
           Opprettes i handlingen "Del utkast"
           Status: Kladd/utkast -> Utkast
           @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/utkast/{deltakerId}/del-med-innbygger") {
                val deltakerId = call.getDeltakerId()
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

                val pameldingRequest = call.receive<EnkeltplassPameldingRequest>()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = personident,
                )

                val deltakerResponse = enkeltplassClient
                    .delUtkastMedInnbygger(
                        deltakerId = deltakerId,
                        pameldingDecoratedRequest = EnkeltplassPameldingDecoratedRequest(
                            wrappedRequest = pameldingRequest.sanitized(),
                            endretAvEnhet = call.getEnhetsnummer(),
                            endretAv = call.getNavIdent(),
                        ),
                    ).let { ModelMapper.toDeltaker(it) }
                    .let { DeltakerResponse.fromDeltakerModel(it) }

                call.respond(deltakerResponse)
            }

            /*
           Direktepåmelding av enkeltplass  deltaker uten at utkast/deltakelsen er delt med innbygger
           Handling: "Meld på uten å dele utkast"
           Status Kladd/Utkast -> søkt inn
           @Returns no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
             */
            post("/utkast/{deltakerId}/meld-paa-direkte") {
                val deltakerId = call.getDeltakerId()

                tilgangskontrollService.verifiserSkrivetilgang(
                    navAnsattAzureId = call.getNavAnsattAzureId(),
                    norskIdent = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
                )

                val pameldingRequest: EnkeltplassPameldingRequest = call.receive()

                enkeltplassClient.meldPaaDirekte(
                    deltakerId = deltakerId,
                    pameldingDecoratedRequest = EnkeltplassPameldingDecoratedRequest(
                        wrappedRequest = pameldingRequest.sanitized(),
                        endretAvEnhet = call.getEnhetsnummer(),
                        endretAv = call.getNavIdent(),
                    ),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
