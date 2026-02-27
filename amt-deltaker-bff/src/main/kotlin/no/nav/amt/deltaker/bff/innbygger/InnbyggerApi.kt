package no.nav.amt.deltaker.bff.innbygger

import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getPersonIdent
import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.innbygger.model.InnbyggerDeltakerResponse
import no.nav.amt.deltaker.bff.innbygger.model.toInnbyggerDeltakerResponse
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.objectMapper

fun Routing.registerInnbyggerApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    tilgangskontrollService: TilgangskontrollService,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    innbyggerService: InnbyggerService,
    forslageRepository: ForslagRepository,
) {
    val scope = CoroutineScope(Dispatchers.IO)

    fun komplettInnbyggerDeltakerResponse(deltaker: Deltaker): InnbyggerDeltakerResponse = deltaker.toInnbyggerDeltakerResponse(
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        forslag = forslageRepository.getForDeltaker(deltaker.id),
    )

    authenticate(AuthLevel.INNBYGGER.name) {
        // kaller amtDeltakerClient.sistBesokt
        get("/innbygger/{deltakerId}") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = deltaker.navBruker.personident,
            )

            scope.launch { deltakerService.oppdaterSistBesokt(deltaker) }

            call.respond(komplettInnbyggerDeltakerResponse(deltaker))
        }

        // kaller paameldingClient.innbyggerGodkjennUtkast
        post("/innbygger/{deltakerId}/godkjenn-utkast") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = deltaker.navBruker.personident,
            )

            // duplikatkode i InnbyggerService
            require(deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                "Deltaker ${deltaker.id} har ikke status ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}"
            }

            val oppdatertDeltaker = innbyggerService.godkjennUtkast(deltaker)

            MetricRegister.GODKJENT_UTKAST.inc()

            call.respond(komplettInnbyggerDeltakerResponse(oppdatertDeltaker))
        }

        // kaller ikke amt-deltaker
        get("/innbygger/{deltakerId}/historikk") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = deltaker.navBruker.personident,
            )

            val historikk = deltaker.getDeltakerHistorikkForVisning()

            val historikkResponse = historikk.toResponse(
                enheter = navEnhetService.hentEnheterForHistorikk(historikk),
                ansatte = navAnsattService.hentAnsatteForHistorikk(historikk),
                arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                oppstartstype = deltaker.deltakerliste.oppstart,
            )

            call.respondText(
                objectMapper.writePolymorphicListAsString(historikkResponse),
                ContentType.Application.Json,
            )
        }
    }
}
