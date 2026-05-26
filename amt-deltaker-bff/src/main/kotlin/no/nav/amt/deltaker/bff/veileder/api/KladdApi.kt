package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.Kladd
import no.nav.amt.deltaker.bff.model.Pamelding
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.sanitize
import no.nav.amt.deltaker.bff.veileder.api.request.valider
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.request.toInnholdModel
import no.nav.amt.internapi.paamelding.request.KladdRequest
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import org.slf4j.LoggerFactory

fun Routing.registerKladdApi(
    tilgangskontrollService: TilgangskontrollService,
    deltakerRepository: DeltakerRepository,
    pameldingService: PameldingService,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    forslageRepository: ForslagRepository,
    amtDistribusjonClient: AmtDistribusjonClient,
    amtDeltakerClient: AmtDeltakerClient,
    paameldingClient: PaameldingClient,
) {
    val log = LoggerFactory.getLogger(javaClass)

    // Skal fases ut ifm henting av data fra amt-deltaker
    suspend fun komplettDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse.fromDeltaker(
        deltaker = deltaker,
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident),
        forslag = forslageRepository.getForDeltaker(deltaker.id),
    )

    authenticate(AuthLevel.VEILEDER.name) {
        post("/kladd") {
            val request = call.receive<OpprettKladdRequest>()

            tilgangskontrollService.verifiserSkrivetilgang(call.getNavAnsattAzureId(), request.personident)

            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = request.deltakerlisteId,
                personIdent = request.personident,
            )

            call.respond(komplettDeltakerResponse(deltaker))
        }

        post("/kladd/{deltakerId}") {
            val request = call.receive<KladdRequest>().sanitize()
            val deltakerId = call.getDeltakerId()
            val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
            request.valider(deltaker)

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )
            // 14 dager etter denne koden er prodsatt så
            // er det trygt å anta at amt-deltaker har siste versjon av alle kladder
            paameldingClient
                .oppdaterKladd(deltakerId, request)

            // Denne koden skal slettes og det er deltakeren fra amt-deltaker som skal returneres
            val nyKladd = pameldingService.upsertKladd(
                kladd = Kladd(
                    opprinneligDeltaker = deltaker,
                    pamelding = Pamelding(
                        deltakelsesinnhold = Deltakelsesinnhold(
                            deltaker.deltakelsesinnhold?.ledetekst,
                            request.innhold.toInnholdModel(deltaker.deltakerliste.tiltak),
                        ),
                        bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                        deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                        dagerPerUke = request.dagerPerUke?.toFloat(),
                        endretAv = call.getNavIdent(),
                        endretAvEnhet = call.getEnhetsnummer(),
                    ),
                ),
            )

            nyKladd
                ?.let { call.respond(HttpStatusCode.OK) }
                ?: call.respond(HttpStatusCode.BadRequest, "Kladden ble ikke opprettet")
        }

        delete("/kladd/{deltakerId}") {
            val deltakerId = call.getDeltakerId()

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
            )

            if (!pameldingService.slettKladd(deltakerId)) {
                call.respond(HttpStatusCode.BadRequest, "Kan ikke slette deltaker")
            }

            log.info("${call.getNavIdent()} har slettet kladd for deltaker med id $deltakerId")

            call.respond(HttpStatusCode.OK)
        }
    }
}
