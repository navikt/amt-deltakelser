package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.model.Pamelding
import no.nav.amt.deltaker.bff.model.Utkast
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.request.toInnholdModel
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun Routing.registerPameldingApi(
    tilgangskontrollService: TilgangskontrollService,
    amtDistribusjonClient: AmtDistribusjonClient,
    amtDeltakerClient: AmtDeltakerClient,
    pameldingClient: PaameldingClient,
) {
    authenticate(AuthLevel.VEILEDER.name) {
        /*
            Oppretter/endrer utkast for en deltaker.
            Handling: "Del utkast" /"Del endret utkast"
            Status: Kladd/utkast -> Utkast
            @Return no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
         */
        post("/pamelding/{deltakerId}") {
            val request = call.receive<UtkastRequest>()

            val deltaker = amtDeltakerClient
                .getDeltaker(call.getDeltakerId())
                .let { ModelMapper.toDeltaker(it) }
            val digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident)

            request.valider(deltaker, digitalBruker)

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            val utkast = Utkast(
                deltakerId = deltaker.id,
                pamelding = Pamelding(
                    deltakelsesinnhold = Deltakelsesinnhold(
                        ledetekst = deltaker.deltakelsesinnhold?.ledetekst,
                        innhold = request.innhold.toInnholdModel(deltaker.gjennomforing.tiltak),
                    ),
                    bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                    deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                    dagerPerUke = request.dagerPerUke?.toFloat(),
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                ),
                godkjentAvNav = false,
            )

            MetricRegister.DELT_UTKAST.inc()

            pameldingClient
                .utkast(utkast)
                .let { ModelMapper.toDeltaker(it) }
                .let { DeltakerResponse.fromDeltakerModel(it) }
                .also { call.respond(it) }
        }

        post("/pamelding/{deltakerId}/avbryt") {
            val deltakerId = call.getDeltakerId()
            val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)
            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = personident,
            )
            pameldingClient.avbrytUtkast(
                deltakerId = deltakerId,
                avbruttAv = call.getNavIdent(),
                avbruttAvEnhet = call.getEnhetsnummer(),
            )

            MetricRegister.AVBRUTT_UTKAST.inc()

            call.respond(HttpStatusCode.OK)
        }

        /*
           Direktepåmelding av deltaker uten at utkast/deltakelsen er delt med innbygger
           Handling: "Meld på uten å dele utkast"
           Status Kladd/Utkast -> Venter på oppstart/søkt inn
         */
        post("/pamelding/{deltakerId}/utenGodkjenning") {
            val request = call.receive<PameldingUtenGodkjenningRequest>()
            val deltaker = amtDeltakerClient
                .getDeltaker(call.getDeltakerId())
                .let { ModelMapper.toDeltaker(it) }

            request.valider(deltaker)
            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            // kaller paameldingClient.utkast
            val utkast = Utkast(
                deltakerId = deltaker.id,
                pamelding = Pamelding(
                    deltakelsesinnhold = Deltakelsesinnhold(
                        innhold = request.innhold.toInnholdModel(deltaker.gjennomforing.tiltak),
                        ledetekst = deltaker.gjennomforing.tiltak.innhold
                            ?.ledetekst,
                    ),
                    bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                    deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                    dagerPerUke = request.dagerPerUke?.toFloat(),
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                ),
                godkjentAvNav = true,
            )

            pameldingClient.utkast(utkast)

            if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                MetricRegister.MELDT_PA_DIREKTE_MED_UTKAST.inc()
            } else {
                MetricRegister.MELDT_PA_DIREKTE_UTEN_UTKAST.inc()
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
