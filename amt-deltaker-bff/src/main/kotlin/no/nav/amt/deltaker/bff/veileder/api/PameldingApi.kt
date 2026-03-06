package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Pamelding
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.veileder.api.model.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.model.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.model.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.model.toInnholdModel
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun Routing.registerPameldingApi(
    tilgangskontrollService: TilgangskontrollService,
    deltakerRepository: DeltakerRepository,
    pameldingService: PameldingService,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    forslageRepository: ForslagRepository,
    amtDistribusjonClient: AmtDistribusjonClient,
) {
    // duplikat i DeltakerApi
    suspend fun komplettDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse.fromDeltaker(
        deltaker = deltaker,
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident),
        forslag = forslageRepository.getForDeltaker(deltaker.id),
    )

    authenticate("VEILEDER") {
        post("/pamelding/{deltakerId}") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()
            val digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident)

            val request = call.receive<UtkastRequest>()
            request.valider(deltaker, digitalBruker)

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            val oppdatertDeltaker = pameldingService.upsertUtkast(
                Utkast(
                    deltakerId = deltaker.id,
                    pamelding = Pamelding(
                        deltakelsesinnhold = Deltakelsesinnhold(
                            ledetekst = deltaker.deltakelsesinnhold?.ledetekst,
                            innhold = request.innhold.toInnholdModel(deltaker),
                        ),
                        bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                        deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                        dagerPerUke = request.dagerPerUke?.toFloat(),
                        endretAv = call.getNavIdent(),
                        endretAvEnhet = call.getEnhetsnummer(),
                    ),
                    godkjentAvNav = false,
                ),
            )

            MetricRegister.DELT_UTKAST.inc()

            call.respond(komplettDeltakerResponse(oppdatertDeltaker))
        }

        post("/pamelding/{deltakerId}/avbryt") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()
            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            pameldingService.avbrytUtkast(
                deltaker = deltaker,
                avbruttAv = call.getNavIdent(),
                avbruttAvEnhet = call.getEnhetsnummer(),
            )

            MetricRegister.AVBRUTT_UTKAST.inc()

            call.respond(HttpStatusCode.OK)
        }

        post("/pamelding/{deltakerId}/utenGodkjenning") {
            val request = call.receive<PameldingUtenGodkjenningRequest>()
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            request.valider(deltaker)
            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            // kaller paameldingClient.utkast
            pameldingService.upsertUtkast(
                Utkast(
                    deltakerId = deltaker.id,
                    pamelding = Pamelding(
                        deltakelsesinnhold = Deltakelsesinnhold(
                            innhold = request.innhold.toInnholdModel(deltaker),
                            ledetekst = deltaker.deltakerliste.tiltak.innhold
                                ?.ledetekst,
                        ),
                        bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                        deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                        dagerPerUke = request.dagerPerUke?.toFloat(),
                        endretAv = call.getNavIdent(),
                        endretAvEnhet = call.getEnhetsnummer(),
                    ),
                    godkjentAvNav = true,
                ),
            )

            if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                MetricRegister.MELDT_PA_DIREKTE_MED_UTKAST.inc()
            } else {
                MetricRegister.MELDT_PA_DIREKTE_UTEN_UTKAST.inc()
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}

fun ApplicationRequest.headerNotNull(navn: String): String {
    val header = call.request.header(navn)
    require(header != null) { "Påkrevd header: $navn er null" }
    return header
}
