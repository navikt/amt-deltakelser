package no.nav.amt.deltaker.bff.innbygger.api

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
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.innbygger.InnbyggerService
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.amt.lib.utils.writePolymorphicListAsString

fun Routing.registerInnbyggerApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    amtDeltakerClient: AmtDeltakerClient,
    tilgangskontrollService: TilgangskontrollService,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    innbyggerService: InnbyggerService,
    forslageRepository: ForslagRepository,
    unleashToggle: CommonUnleashToggle,
    pameldingClient: PaameldingClient,
) {
    val scope = CoroutineScope(Dispatchers.IO)

    // Denne skal fases ut når når vi alltid kan hente data fra amt-deltaker
    fun komplettInnbyggerDeltakerResponse(deltaker: Deltaker): InnbyggerDeltakerResponse = deltaker.toInnbyggerDeltakerResponse(
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        forslag = forslageRepository.getForDeltaker(deltaker.id),
    )

    authenticate(AuthLevel.INNBYGGER.name) {
        /*
            Henter en komplett deltaker for innbyggers flate
            Dette endepunktet nås når en innbygger trykker seg inn på aktivitetskort "detaljer om deltakelsen" i aktivitetsplanen
         */
        get("/innbygger/{deltakerId}") {
            val deltakerId = call.getDeltakerId()
            val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId).personident

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = personident,
            )

            val deltakerResponse =
                if (unleashToggle.prioriterSynkronKommunikasjon()) {
                    amtDeltakerClient
                        .getDeltaker(deltakerId)
                        .let { ModelMapper.toDeltaker(it) }
                        .let { deltaker -> InnbyggerDeltakerResponse.fromModel(deltaker) }
                } else {
                    val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
                    komplettInnbyggerDeltakerResponse(deltaker)
                }

            scope.launch { deltakerService.oppdaterSistBesokt(deltakerId) }

            call.respond(deltakerResponse)
        }

        /*
            Endepunkt når innbygger godkjenner utkast ("deltakelse delt med bruker")
            Status: Utkast til påmelding -> Søkt inn/Venter på oppstart/Deltaker(kommer ann på tiltakstypen)
            gjør synkronkall til amt-deltaker med dataene som returnerer et mindre "Deltakeroppdatering" objekt
         */
        post("/innbygger/{deltakerId}/godkjenn-utkast") {
            val deltakerId = call.getDeltakerId()

            val deltakerResponse = if (unleashToggle.prioriterSynkronKommunikasjon()) {
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId).personident
                tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                    rekvirentPersonident = call.getPersonIdent(),
                    ressursPersonident = personident,
                )
                pameldingClient.innbyggerGodkjennUtkast(deltakerId)
                amtDeltakerClient
                    .getDeltaker(deltakerId)
                    .let { ModelMapper.toDeltaker(it) }
                    .let { deltaker -> InnbyggerDeltakerResponse.fromModel(deltaker) }
            } else {
                val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

                tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                    rekvirentPersonident = call.getPersonIdent(),
                    ressursPersonident = deltaker.navBruker.personident,
                )

                innbyggerService
                    .godkjennUtkast(deltaker)
                    .let { oppdatertDeltaker -> komplettInnbyggerDeltakerResponse(oppdatertDeltaker) }
            }

            MetricRegister.GODKJENT_UTKAST.inc()

            call.respond(deltakerResponse)
        }

        // henter all historikkdata fra amt-deltaker når prioriterSynkronKommunikasjon-toggle er aktiv,
        // ellers brukes lokal historikk fra deltaker
        get("/innbygger/{deltakerId}/historikk") {
            val deltakerId = call.getDeltakerId()

            val historikkResponse = if (unleashToggle.prioriterSynkronKommunikasjon()) {
                val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId).personident
                tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                    rekvirentPersonident = call.getPersonIdent(),
                    ressursPersonident = personident,
                )
                val data = amtDeltakerClient.getDeltakerHistorikkData(deltakerId)
                DeltakerHistorikkResponse.fromModels(
                    models = data.historikk,
                    arrangornavn = data.arrangornavn,
                    oppstartstype = data.oppstartstype,
                    pameldingstype = data.pameldingstype,
                    enheter = data.enheter,
                    ansatte = data.ansatte,
                )
            } else {
                val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
                tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                    rekvirentPersonident = call.getPersonIdent(),
                    ressursPersonident = deltaker.navBruker.personident,
                )
                val historikk = deltaker.getDeltakerHistorikkForVisning()
                DeltakerHistorikkResponse.fromModels(
                    models = historikk,
                    arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                    oppstartstype = deltaker.deltakerliste.oppstart,
                    pameldingstype = deltaker.deltakerliste.pameldingstype,
                    enheter = navEnhetService.hentEnheterForHistorikk(historikk),
                    ansatte = navAnsattService.hentAnsatteForHistorikk(historikk),
                )
            }

            call.respondText(
                objectMapper.writePolymorphicListAsString(historikkResponse),
                ContentType.Application.Json,
            )
        }
    }
}
