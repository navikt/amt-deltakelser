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
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.deltaker.bff.veileder.api.response.tilUtflatetKodeverk
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString

fun Routing.registerInnbyggerApi(
    deltakerService: DeltakerService,
    amtDeltakerClient: AmtDeltakerClient,
    tilgangskontrollService: TilgangskontrollService,
    pameldingClient: PaameldingClient,
    kodeverkClient: KodeverkClient,
) {
    val scope = CoroutineScope(Dispatchers.IO)

    authenticate(AuthLevel.INNBYGGER.name) {
        /*
            Henter en komplett deltaker for innbyggers flate
            Dette endepunktet nås når en innbygger trykker seg inn på aktivitetskort "detaljer om deltakelsen" i aktivitetsplanen
         */
        get("/innbygger/{deltakerId}") {
            val deltakerId = call.getDeltakerId()

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
            )

            val deltakerResponse = amtDeltakerClient
                .getDeltaker(deltakerId)
                .let { ModelMapper.toDeltaker(it) }
                .let { deltakerModel ->
                    val utflatetKodeverk = deltakerModel.gjennomforing.tiltak.tiltakskode
                        .takeIf { deltakerModel.gjennomforing.type == GjennomforingType.Enkeltplass }
                        ?.let { tiltakskode ->
                            kodeverkClient
                                .hentKodeverk(tiltakskode)
                                .tilUtflatetKodeverk(
                                    kodeverkValg = deltakerModel.gjennomforing.kodeverkValg,
                                    sertifiseringValg = deltakerModel.gjennomforing.sertifiseringValg,
                                )
                        }

                    InnbyggerDeltakerResponse.fromModel(
                        deltaker = deltakerModel,
                        utflatetKodeverk = utflatetKodeverk,
                    )
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

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
            )
            pameldingClient.innbyggerGodkjennUtkast(deltakerId)
            val deltakerResponse = amtDeltakerClient
                .getDeltaker(deltakerId)
                .let { ModelMapper.toDeltaker(it) }
                .let { deltaker ->
                    InnbyggerDeltakerResponse.fromModel(
                        deltaker = deltaker,
                        utflatetKodeverk = null,
                    )
                }

            MetricRegister.GODKJENT_UTKAST.inc()

            call.respond(deltakerResponse)
        }

        get("/innbygger/{deltakerId}/historikk") {
            val deltakerId = call.getDeltakerId()

            tilgangskontrollService.verifiserInnbyggersTilgangTilDeltaker(
                rekvirentPersonident = call.getPersonIdent(),
                ressursPersonident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
            )
            val data = amtDeltakerClient.getDeltakerHistorikkData(deltakerId)
            val historikkResponse = DeltakerHistorikkResponse.fromModels(
                models = data.historikk,
                arrangornavn = data.arrangornavn,
                oppstartstype = data.oppstartstype,
                pameldingstype = data.pameldingstype,
                enheter = data.enheter,
                ansatte = data.ansatte,
            )
            call.respondText(
                objectMapper.writePolymorphicListAsString(historikkResponse),
                ContentType.Application.Json,
            )
        }
    }
}
