package no.nav.amt.deltaker.bff.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.api.model.DeltakerResponse
import no.nav.amt.deltaker.bff.deltaker.api.model.KladdRequest
import no.nav.amt.deltaker.bff.deltaker.api.model.OpprettNyKladdRequest
import no.nav.amt.deltaker.bff.deltaker.api.model.toInnholdModel
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Kladd
import no.nav.amt.deltaker.bff.deltaker.model.Pamelding
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
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
) {
    val log = LoggerFactory.getLogger(javaClass)

    // duplikat i DeltakerApi
    suspend fun komplettDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse.fromDeltaker(
        deltaker = deltaker,
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident),
        forslag = forslageRepository.getForDeltaker(deltaker.id),
    )

    post("/kladd") {
        // Erstatter /pamelding
        val request = call.receive<OpprettNyKladdRequest>()

        tilgangskontrollService.verifiserSkrivetilgang(call.getNavAnsattAzureId(), request.personident)

        val deltaker = pameldingService.opprettDeltaker(
            deltakerlisteId = request.deltakerlisteId,
            personIdent = request.personident,
        )

        call.respond(komplettDeltakerResponse(deltaker))
    }

    // Dette endepunktet kommuniserer ikke med amt-deltaker
    post("/kladd/{deltakerId}") {
        // Erstatter /pamelding/{deltakerId}/kladd
        val request = call.receive<KladdRequest>().sanitize()

        val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()
        request.valider(deltaker)

        tilgangskontrollService.verifiserSkrivetilgang(
            navAnsattAzureId = call.getNavAnsattAzureId(),
            norskIdent = deltaker.navBruker.personident,
        )

        val nyKladd = pameldingService.upsertKladd(
            kladd = Kladd(
                opprinneligDeltaker = deltaker,
                pamelding = Pamelding(
                    deltakelsesinnhold = Deltakelsesinnhold(
                        deltaker.deltakelsesinnhold?.ledetekst,
                        request.innhold.toInnholdModel(deltaker),
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
        // Erstatter delete /pamelding/{deltakerId}/kladd

        val deltakerId = call.getDeltakerId()
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        tilgangskontrollService.verifiserSkrivetilgang(
            navAnsattAzureId = call.getNavAnsattAzureId(),
            norskIdent = deltaker.navBruker.personident,
        )

        if (!pameldingService.slettKladd(deltaker)) {
            call.respond(HttpStatusCode.BadRequest, "Kan ikke slette deltaker")
        }

        log.info("${call.getNavIdent()} har slettet kladd for deltaker med id $deltakerId")

        call.respond(HttpStatusCode.OK)
    }
}
