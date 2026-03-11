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
import jakarta.ws.rs.ForbiddenException
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.deltaker.ModelMapper
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.extensions.getEnhetsnummer
import no.nav.amt.deltaker.bff.extensions.getForslagId
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
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
import no.nav.amt.deltaker.bff.veileder.api.request.Endringsrequest
import no.nav.amt.deltaker.bff.veileder.api.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.IkkeAktuellRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.toInnholdModel
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.response.toResponse
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Routing.registerVeilederApi(
    tilgangskontrollService: TilgangskontrollService,
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    forslagRepository: ForslagRepository,
    forslagService: ForslagService,
    amtDistribusjonClient: AmtDistribusjonClient,
    amtDeltakerClient: AmtDeltakerClient,
    sporbarhetsloggService: SporbarhetsloggService,
    unleashToggle: CommonUnleashToggle,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    // duplikat i PameldiongApi
    suspend fun komplettDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse.fromDeltaker(
        deltaker = deltaker,
        ansatte = navAnsattService.hentAnsatteForDeltaker(deltaker),
        vedtakSistEndretAvEnhet = deltaker.vedtaksinformasjon?.sistEndretAvEnhet?.let { navEnhetService.hentEnhet(it) },
        digitalBruker = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident),
        forslag = forslagRepository.getForDeltaker(deltaker.id),
    )

    fun illegalUpdateGuard(
        deltaker: Deltaker,
        tillatEndringUtenOppfPeriode: Boolean,
    ) {
        if (!deltaker.kanEndres) {
            log.error("Kan ikke endre deltaker med id ${deltaker.id} som er låst")
            throw ForbiddenException("Kan ikke endre låst deltaker ${deltaker.id}")
        }

        if (deltaker.status.type == DeltakerStatus.Type.FEILREGISTRERT) {
            throw ForbiddenException("Kan ikke endre låst deltaker ${deltaker.id}")
        }

        if (!unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltak.tiltakskode)) {
            throw ForbiddenException("Kan ikke utføre endring på deltaker ${deltaker.id}")
        }

        if (!deltaker.navBruker.harAktivOppfolgingsperiode && !tillatEndringUtenOppfPeriode) {
            log.warn("Kan ikke endre deltaker med id ${deltaker.id} som ikke har aktiv oppfølgingsperiode")
            throw IllegalArgumentException("Kan ikke endre deltaker som ikke har aktiv oppfølgingsperiode")
        }
    }

    suspend fun ApplicationCall.handleEndring(
        request: Endringsrequest,
        produceEndringRequest: (deltaker: Deltaker, endretAv: String, endretAvEnhet: String) -> EndringRequest,
    ) {
        val deltaker = deltakerRepository.get(this.getDeltakerId()).getOrThrow()

        tilgangskontrollService.verifiserSkrivetilgang(
            navAnsattAzureId = this.getNavAnsattAzureId(),
            norskIdent = deltaker.navBruker.personident,
        )
        illegalUpdateGuard(
            deltaker = deltaker,
            tillatEndringUtenOppfPeriode = request.tillattEndringUtenAktivOppfolgingsperiode(),
        )

        request.valider(deltaker)

        val oppdatertDeltaker = deltakerService.oppdaterDeltaker(
            deltaker = deltaker,
            endringRequest = produceEndringRequest(
                deltaker,
                this.getNavIdent(),
                this.getEnhetsnummer(),
            ),
        )

        this.respond(komplettDeltakerResponse(oppdatertDeltaker))
    }

    authenticate(AuthLevel.VEILEDER.name) {
        post("/deltaker/{deltakerId}") {
            val request = call.receive<DeltakerRequest>()
            val deltakerId = call.getDeltakerId()
            val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

            if (request.personident != deltaker.navBruker.personident) {
                log.warn("${deltaker.id} ble forsøkt lest med annen Nav-bruker i kontekst.")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            tilgangskontrollService.verifiserLesetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            sporbarhetsloggService.sendAuditLog(
                navIdent = call.getNavIdent(),
                deltakerPersonIdent = deltaker.navBruker.personident,
            )
            val deltakerResponse =
                if (unleashToggle.prioriterSynkronKommunikasjon()) {
                    amtDeltakerClient
                        .getDeltaker(deltakerId)
                        .let { ModelMapper.toDeltaker(it) }
                        .let { DeltakerResponse.fromDeltakerModel(it) }
                } else {
                    komplettDeltakerResponse(deltaker)
                }

            call.respond(deltakerResponse)
        }

        // kaller ikke amt-deltaker
        get("/deltaker/{deltakerId}/historikk") {
            val deltaker = deltakerRepository.get(call.getDeltakerId()).getOrThrow()
            tilgangskontrollService.verifiserLesetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            log.info("Nav-ident ${call.getNavIdent()} har gjort oppslag på historikk for deltaker med id ${deltaker.id}")

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

        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            val request = call.receive<EndreBakgrunnsinformasjonRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                BakgrunnsinformasjonRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    bakgrunnsinformasjon = request.bakgrunnsinformasjon,
                )
            }
        }

        post("/deltaker/{deltakerId}/innhold") {
            val request = call.receive<EndreInnholdRequest>()
            call.handleEndring(request) { deltaker, endretAv, endretAvEnhet ->
                InnholdRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    deltakelsesinnhold = Deltakelsesinnhold(
                        innhold = request.innhold.toInnholdModel(deltaker),
                        ledetekst = deltaker.deltakerliste.tiltak.innhold
                            ?.ledetekst,
                    ),
                )
            }
        }

        post("/deltaker/{deltakerId}/deltakelsesmengde") {
            val request = call.receive<EndreDeltakelsesmengdeRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                DeltakelsesmengdeRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    deltakelsesprosent = request.deltakelsesprosent,
                    dagerPerUke = request.dagerPerUke,
                    gyldigFra = request.gyldigFra,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/startdato") {
            val request = call.receive<EndreStartdatoRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                StartdatoRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    startdato = request.startdato,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/sluttdato") {
            val request = call.receive<EndreSluttdatoRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                SluttdatoRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/sluttarsak") {
            val request = call.receive<EndreSluttarsakRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                SluttarsakRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    aarsak = request.aarsak,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/ikke-aktuell") {
            val request = call.receive<IkkeAktuellRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    aarsak = request.aarsak,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/reaktiver") {
            val request = call.receive<ReaktiverDeltakelseRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/avslutt") {
            val request = call.receive<AvsluttDeltakelseRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                // code-review note: Denne logikken bør flyttes til amt-deltaker
                when {
                    request.harDeltatt() && request.harFullfort() -> {
                        require(request.sluttdato != null) { "Sluttdato er påkrevd for å avslutte deltakelse" }
                        no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest(
                            endretAv = endretAv,
                            endretAvEnhet = endretAvEnhet,
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
                            endretAvEnhet = endretAvEnhet,
                            forslagId = request.forslagId,
                            sluttdato = request.sluttdato,
                            aarsak = request.aarsak,
                            begrunnelse = request.begrunnelse,
                        )
                    }

                    else -> {
                        require(request.aarsak != null) { "Årsak er påkrevd for å sette deltaker til ikke aktuell" }
                        no.nav.amt.lib.models.deltaker.internalapis.deltaker.request
                            .IkkeAktuellRequest(
                                endretAv = endretAv,
                                endretAvEnhet = endretAvEnhet,
                                forslagId = request.forslagId,
                                aarsak = request.aarsak,
                                begrunnelse = request.begrunnelse,
                            )
                    }
                }
            }
        }

        post("/deltaker/{deltakerId}/endre-avslutning") {
            val request = call.receive<EndreAvslutningRequest>()

            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                // code-review note: Denne logikken bør flyttes til amt-deltaker
                if (request.harDeltatt()) {
                    no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest(
                        endretAv = endretAv,
                        endretAvEnhet = endretAvEnhet,
                        forslagId = request.forslagId,
                        sluttdato = request.sluttdato,
                        aarsak = request.aarsak,
                        begrunnelse = request.begrunnelse,
                        harFullfort = request.harFullfort,
                    )
                } else {
                    require(request.aarsak != null) { "Årsak er påkrevd for å sette deltaker til ikke aktuell" }
                    no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest(
                        endretAv = endretAv,
                        endretAvEnhet = endretAvEnhet,
                        forslagId = request.forslagId,
                        aarsak = request.aarsak,
                        begrunnelse = request.begrunnelse,
                    )
                }
            }
        }

        post("/deltaker/{deltakerId}/forleng") {
            val request = call.receive<ForlengDeltakelseRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    sluttdato = request.sluttdato,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        post("/deltaker/{deltakerId}/fjern-oppstartsdato") {
            val request = call.receive<FjernOppstartsdatoRequest>()
            call.handleEndring(request) { _, endretAv, endretAvEnhet ->
                no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest(
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    forslagId = request.forslagId,
                    begrunnelse = request.begrunnelse,
                )
            }
        }

        // kaller ikke amt-deltaker
        post("/forslag/{forslagId}/avvis") {
            val request = call.receive<AvvisForslagRequest>()
            val forslag = forslagRepository.get(call.getForslagId()).getOrThrow()
            val deltaker = deltakerRepository.get(forslag.deltakerId).getOrThrow()

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = deltaker.navBruker.personident,
            )

            forslagService.avvisForslag(
                opprinneligForslag = forslag,
                begrunnelse = request.begrunnelse,
                avvistAvAnsatt = call.getNavIdent(),
                avvistAvEnhet = call.getEnhetsnummer(),
            )

            call.respond(komplettDeltakerResponse(deltaker))
        }
    }
}
