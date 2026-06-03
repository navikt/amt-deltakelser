package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.extensions.getForslagId
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.veileder.api.request.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.AvvisForslagRequest
import no.nav.amt.deltaker.bff.veileder.api.request.DeltakerRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreAvslutningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreBakgrunnsinformasjonRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreDeltakelsesmengdeRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreInnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttarsakRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreStartdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndringRequestFromFrontend
import no.nav.amt.deltaker.bff.veileder.api.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.IkkeAktuellRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.response.tilUtflatetKodeverk
import no.nav.amt.internapi.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.EndretInnholdRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.SluttarsakRequest
import no.nav.amt.internapi.deltaker.request.SluttdatoRequest
import no.nav.amt.internapi.deltaker.request.StartdatoRequest
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Routing.registerVeilederApi(
    tilgangskontrollService: TilgangskontrollService,
    forslagRepository: ForslagRepository,
    amtDeltakerClient: AmtDeltakerClient,
    sporbarhetsloggService: SporbarhetsloggService,
    kodeverkClient: KodeverkClient,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun ApplicationCall.handleEndring(
        frontendRequest: EndringRequestFromFrontend,
        amtDeltakerRequest: EndringRequest,
    ) {
        val deltakerId = this.getDeltakerId()

        val deltaker = amtDeltakerClient
            .getDeltaker(deltakerId)
            .let { ModelMapper.toDeltaker(it) }

        tilgangskontrollService.verifiserSkrivetilgang(
            navAnsattAzureId = this.getNavAnsattAzureId(),
            norskIdent = deltaker.navBruker.personident,
        )
        frontendRequest.valider(deltaker)

        amtDeltakerClient
            .postEndreDeltaker(
                deltakerId = deltaker.id,
                requestBody = amtDeltakerRequest,
            ).let { ModelMapper.toDeltaker(it) }
            .let { DeltakerResponse.fromDeltakerModel(it) }
            .also { this.respond(it) }
    }

    authenticate(AuthLevel.VEILEDER.name) {
        post("/deltaker/{deltakerId}") {
            val request = call.receive<DeltakerRequest>()
            val deltakerId = call.getDeltakerId()
            val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

            if (request.personident != personident) {
                log.warn("$deltakerId ble forsøkt lest med annen Nav-bruker i kontekst.")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            tilgangskontrollService.verifiserLesetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = personident,
            )

            sporbarhetsloggService.sendAuditLog(
                navIdent = call.getNavIdent(),
                deltakerPersonIdent = personident,
            )

            amtDeltakerClient
                .getDeltaker(deltakerId)
                .let { deltakerResponse ->
                    val utflatetKodeverk = if (deltakerResponse.gjennomforing.type == GjennomforingType.Enkeltplass) {
                        kodeverkClient
                            .hentKodeverk(deltakerResponse.gjennomforing.tiltakstype.tiltakskode)
                            .tilUtflatetKodeverk(
                                kodeverkValg = deltakerResponse.gjennomforing.kodeverkValg,
                                sertifiseringValg = deltakerResponse.gjennomforing.sertifiseringValg,
                            )
                    } else {
                        null
                    }

                    DeltakerResponse.fromDeltakerModel(
                        deltaker = ModelMapper.toDeltaker(deltakerResponse),
                        utflatetKodeverk = utflatetKodeverk,
                    )
                }.also { call.respond(it) }
        }

        get("/deltaker/{deltakerId}/historikk") {
            val deltakerId = call.getDeltakerId()

            log.info("Nav-ident ${call.getNavIdent()} har gjort oppslag på historikk for deltaker med id $deltakerId")

            val personident = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)
            tilgangskontrollService.verifiserLesetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = personident,
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

        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            val request = call.receive<EndreBakgrunnsinformasjonRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = BakgrunnsinformasjonRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                ),
            )
        }

        post("/deltaker/{deltakerId}/innhold") {
            val request = call.receive<EndreInnholdRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = EndretInnholdRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    innholdselementer = request.innhold,
                ),
            )
        }

        post("/deltaker/{deltakerId}/deltakelsesmengde") {
            val request = call.receive<EndreDeltakelsesmengdeRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = DeltakelsesmengdeRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    deltakelsesprosent = request.deltakelsesprosent,
                    dagerPerUke = request.dagerPerUke,
                    gyldigFra = request.gyldigFra,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/startdato") {
            val request = call.receive<EndreStartdatoRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = StartdatoRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    startdato = request.startdato,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/sluttdato") {
            val request = call.receive<EndreSluttdatoRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = SluttdatoRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/sluttarsak") {
            val request = call.receive<EndreSluttarsakRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = SluttarsakRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    aarsak = request.aarsak,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/ikke-aktuell") {
            val request = call.receive<IkkeAktuellRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    aarsak = request.aarsak,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/reaktiver") {
            val request = call.receive<ReaktiverDeltakelseRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/avslutt") {
            val request = call.receive<AvsluttDeltakelseRequest>()
            val endretAv = call.getNavIdent()
            call.handleEndring(
                frontendRequest = request,
                // code-review note: Denne logikken bør flyttes til amt-deltaker
                amtDeltakerRequest = when {
                    request.harDeltatt() && request.harFullfort() -> {
                        require(request.sluttdato != null) { "Sluttdato er påkrevd for å avslutte deltakelse" }
                        no.nav.amt.internapi.deltaker.request.AvsluttDeltakelseRequest(
                            endretAv = endretAv,
                            endretAvEnhet = call.getEnhetsnummer(),
                            forslagId = request.forslagId,
                            sluttdato = request.sluttdato,
                            aarsak = request.aarsak,
                            begrunnelse = request.begrunnelse,
                            harFullfort = request.harFullfort,
                        )
                    }

                    request.harDeltatt() && !request.harFullfort() -> {
                        require(request.aarsak != null) { "Årsak er påkrevd for å avbryte deltakelse" }
                        require(request.sluttdato != null) { "Sluttdato er påkrevd for å avbryte deltakelse" }
                        AvbrytDeltakelseRequest(
                            endretAv = endretAv,
                            endretAvEnhet = call.getEnhetsnummer(),
                            forslagId = request.forslagId,
                            sluttdato = request.sluttdato,
                            aarsak = request.aarsak,
                            begrunnelse = request.begrunnelse,
                        )
                    }

                    else -> {
                        require(request.aarsak != null) { "Årsak er påkrevd for å sette deltaker til ikke aktuell" }
                        no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest(
                            endretAv = endretAv,
                            endretAvEnhet = call.getEnhetsnummer(),
                            forslagId = request.forslagId,
                            aarsak = request.aarsak,
                            begrunnelse = request.begrunnelse,
                        )
                    }
                },
            )
        }

        post("/deltaker/{deltakerId}/endre-avslutning") {
            val request = call.receive<EndreAvslutningRequest>()

            call.handleEndring(
                frontendRequest = request,
                // code-review note: Denne logikken bør flyttes til amt-deltaker
                amtDeltakerRequest = if (request.harDeltatt()) {
                    no.nav.amt.internapi.deltaker.request.EndreAvslutningRequest(
                        endretAv = call.getNavIdent(),
                        endretAvEnhet = call.getEnhetsnummer(),
                        forslagId = request.forslagId,
                        sluttdato = request.sluttdato,
                        aarsak = request.aarsak,
                        begrunnelse = request.begrunnelse,
                        harFullfort = request.harFullfort,
                    )
                } else {
                    require(request.aarsak != null) { "Årsak er påkrevd for å sette deltaker til ikke aktuell" }
                    no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest(
                        endretAv = call.getNavIdent(),
                        endretAvEnhet = call.getEnhetsnummer(),
                        forslagId = request.forslagId,
                        aarsak = request.aarsak,
                        begrunnelse = request.begrunnelse,
                    )
                },
            )
        }

        post("/deltaker/{deltakerId}/forleng") {
            val request = call.receive<ForlengDeltakelseRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/deltaker/{deltakerId}/fjern-oppstartsdato") {
            val request = call.receive<FjernOppstartsdatoRequest>()
            call.handleEndring(
                frontendRequest = request,
                amtDeltakerRequest = no.nav.amt.internapi.deltaker.request.FjernOppstartsdatoRequest(
                    endretAv = call.getNavIdent(),
                    endretAvEnhet = call.getEnhetsnummer(),
                    forslagId = request.forslagId,
                    begrunnelse = request.begrunnelse,
                ),
            )
        }

        post("/forslag/{forslagId}/avvis") {
            val forslagId = call.getForslagId()
            val navAnsattAzureId = call.getNavAnsattAzureId()
            val request = call.receive<AvvisForslagRequest>()
            val personident = amtDeltakerClient.getPersonidentForForslag(forslagId)

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = navAnsattAzureId,
                norskIdent = personident,
            )

            amtDeltakerClient
                .avvisForslag(
                    forslagId = forslagId,
                    request = no.nav.amt.internapi.deltaker.request.AvvisForslagRequest(
                        begrunnelse = request.begrunnelse,
                        avvistAvAnsatt = navAnsattAzureId,
                        avvistAvEnhet = call.getEnhetsnummer(),
                    ),
                ).let { ModelMapper.toDeltaker(it) }
                .let { DeltakerResponse.fromDeltakerModel(it) }
                .also {
                    // Usikker på om forslag hentes fra bff db noen steder så beholder denne midlertidig:
                    forslagRepository.delete(forslagId)
                    call.respond(it)
                }
        }
    }
}
