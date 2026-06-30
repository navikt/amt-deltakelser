package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.database.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

fun Routing.registerInternalApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    kladdService: KladdService,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    innsokRepository: InnsokRepository,
    vurderingRepository: VurderingRepository,
    distribuerEndringService: DistribuerEndringService,
    endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    vedtakRepository: VedtakRepository,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
    gjennomforingRequestProducer: GjennomforingRequestProducer,
) {
    val scope = CoroutineScope(Dispatchers.IO)
    val log: Logger = LoggerFactory.getLogger(javaClass)

    fun requireInternal(remoteAddress: String) {
        if (!isInternal(remoteAddress)) throw AuthorizationException("Ikke tilgang til api")
    }

    fun slettDeltaker(deltakerId: UUID) = Database.transaction {
        innsokRepository.deleteForDeltaker(deltakerId)
        vurderingRepository.deleteForDeltaker(deltakerId)
        deltakerService.deleteDeltaker(deltakerId)
    }

    fun republiserDeltakere(
        deltakerIder: List<UUID>,
        request: RepubliserRequest,
    ) {
        deltakerIder.forEach { deltakerId ->
            runCatching {
                Database.transaction {
                    deltakerProducerService.produce(
                        deltakerRepository.get(deltakerId).getOrThrow(),
                        forcedUpdate = request.forcedUpdate,
                        publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                        publiserTilDeltakerV2 = request.publiserTilDeltakerV2,
                        publiserTilDeltakerEksternV1 = request.publiserTilDeltakerEksternV1,
                    )
                }
            }.onFailure { e ->
                log.error("Feil ved relast av deltaker $deltakerId", e)
            }
        }
    }

    route("/internal") {
        post("/sett-ikke-aktuell/{fra-status}") {
            requireInternal(call.request.local.remoteAddress)
            scope.launch {
                val fraStatus = DeltakerStatus.Type.valueOf(call.parameters["fra-status"]!!)
                log.info("Mottatt forespørsel for å endre deltakere med status $fraStatus til IKKE_AKTUELL.")
                val deltakerIder = deltakerRepository.getDeltakereMedStatus(fraStatus)

                deltakerIder.forEach {
                    val deltaker = deltakerRepository.get(it).getOrThrow()
                    deltakerService.upsertAndProduceDeltaker(
                        deltaker.copy(
                            status = DeltakerStatus(
                                id = UUID.randomUUID(),
                                type = DeltakerStatus.Type.IKKE_AKTUELL,
                                aarsak = null,
                                gyldigFra = LocalDateTime.now(),
                                gyldigTil = null,
                                opprettet = LocalDateTime.now(),
                            ),
                        ),
                        erDeltakerSluttdatoEndret = false,
                        forceProduce = true,
                    )
                }

                log.info("Oppdatert status til IKKE_AKTUELL for ${deltakerIder.size} deltakere med status $fraStatus.")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/feilregistrer/{deltakerId}") {
            requireInternal(call.request.local.remoteAddress)
            deltakerService.feilregistrerDeltaker(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }

        post("/relast/{deltakerId}") {
            requireInternal(call.request.local.remoteAddress)
            val deltakerId = call.getDeltakerId()
            val request = call.receive<RepubliserRequest>()
            log.info("relast/{deltakerId}: Starter relast av deltaker $deltakerId")
            Database.transaction {
                deltakerProducerService.produce(
                    deltakerRepository.get(deltakerId).getOrThrow(),
                    forcedUpdate = request.forcedUpdate,
                    publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    publiserTilDeltakerV2 = request.publiserTilDeltakerV2,
                    publiserTilDeltakerEksternV1 = request.publiserTilDeltakerEksternV1,
                )
            }
            log.info("relast/{deltakerId}: Fullført relast av deltaker $deltakerId")
            call.respond(HttpStatusCode.OK)
        }

        post("/tving-arena-innlesing") {
            // Brukes i tilfelle man ønsker å tvinge arena til å lese inn en endring
            // selv om det ikke reelt har blitt gjort en endring, som for eksempel
            // når vi har lest inn og transformert status før vi ble master og arena
            // ikke har fått med seg endringen fordi endretDato er før komet ble master
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<RelastDeltakereRequest>()
            log.info("Republiser deltakere:${request.deltakere} deltakere med ny endretDato på deltaker-v1")
            request.deltakere.forEach { deltakerId ->
                Database.transaction {
                    deltakerProducerService.produce(
                        deltakerRepository.get(deltakerId).getOrThrow().copy(sistEndret = LocalDateTime.now()),
                        forcedUpdate = request.republiserRequest.forcedUpdate,
                        publiserTilDeltakerV1 = request.republiserRequest.publiserTilDeltakerV1,
                        publiserTilDeltakerEksternV1 = request.republiserRequest.publiserTilDeltakerEksternV1,
                        publiserTilDeltakerV2 = false,
                    )
                }
            }
            log.info("Republiserte ${request.deltakere.size} på deltaker-v1")
            call.respond(HttpStatusCode.OK)
        }

        post("/relast/tiltakstype/{tiltakskode}") {
            requireInternal(call.request.local.remoteAddress)
            val tiltakskode = Tiltakskode.valueOf(
                call.parameters["tiltakskode"] ?: throw IllegalArgumentException("Tiltakskode ikke satt"),
            )
            val request = call.receive<RepubliserRequest>()
            scope.launch {
                val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                log.info("relast/tiltakstype: Starter relast av ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
                republiserDeltakere(deltakerIder, request)
                log.info("relast/tiltakstype: Fullført relast av ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/relast/tiltakstyper") {
            requireInternal(call.request.local.remoteAddress)
            val requestBody = call.receive<RepubliserTiltakskoderRequest>()
            scope.launch {
                val tiltakskodeNavn = requestBody.tiltakskoder.map { it.name }
                log.info("relast/tiltakstyper: Starter relast for tiltakskoder $tiltakskodeNavn")
                requestBody.tiltakskoder.forEach { tiltakskode ->
                    val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                    republiserDeltakere(deltakerIder, requestBody.request)
                }
                log.info("relast/tiltakstyper: Fullført relast for tiltakskoder $tiltakskodeNavn")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/relast/alle-deltakere") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<RepubliserRequest>()
            scope.launch {
                log.info("relast/alle-deltakere: Starter relast av alle deltakere komet er master for")
                for (tiltakskode in Tiltakskode.entries) {
                    val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                    log.info("relast/alle-deltakere: Relaster ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
                    republiserDeltakere(deltakerIder, request)
                    log.info("relast/alle-deltakere: Fullført ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
                }
                log.info("relast/alle-deltakere: Fullført relast av alle deltakere")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/relast/deltakere") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<RelastDeltakereRequest>()
            scope.launch {
                log.info("relast/deltakere: Starter relast av ${request.deltakere.size} deltakere")
                republiserDeltakere(request.deltakere, request.republiserRequest)
                log.info("relast/deltakere: Fullført relast av ${request.deltakere.size} deltakere")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/slett-deltakere") {
            requireInternal(call.request.local.remoteAddress)
            if (!Environment.isDev()) throw IllegalStateException("Kan kun slette deltaker i dev")
            val request = call.receive<DeleteDeltakereRequest>()
            scope.launch {
                log.info("slett-deltakere: Starter sletting av ${request.deltakere.size} deltakere")
                request.deltakere.forEach { deltakerId ->
                    deltakerProducerService.tombstone(deltakerId)
                    slettDeltaker(deltakerId)
                }
                log.info("slett-deltakere: Fullført sletting av ${request.deltakere.size} deltakere")
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/slett-kladd") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<DeleteDeltakereRequest>()
            scope.launch {
                log.info("slett-kladd: Starter sletting av ${request.deltakere.size} kladder")
                request.deltakere.forEach { deltakerId -> kladdService.slettKladd(deltakerId) }
                log.info("slett-kladd: Fullført sletting av ${request.deltakere.size} kladder")
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/avbryt-utkast/{deltakerId}") {
            requireInternal(call.request.local.remoteAddress)
            val deltakerId = call.parameters.getOrFail("deltakerId").let { UUID.fromString(it) }
            val status = nyDeltakerStatus(
                DeltakerStatus.Type.AVBRUTT_UTKAST,
                DeltakerStatus.Aarsak(
                    type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
                    beskrivelse = null,
                ),
            )
            deltakerService.upsertAndProduceDeltaker(
                deltaker = deltakerRepository.get(deltakerId).getOrThrow(),
                erDeltakerSluttdatoEndret = false,
                beforeUpsert = { deltaker ->
                    val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker)
                    deltaker.copy(status = status, vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
                },
            )
        }

        post("/relast/hendelse-fra-tiltakskoordinator") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<RelastHendelseRequest>()
            scope.launch {
                log.info("Relaster hendelse med endringid: ${request.endringId}")
                val endring = endringFraTiltakskoordinatorRepository.get(request.endringId)
                    ?: throw IllegalArgumentException(
                        "Kunne ikke relaste hendelse med endring med id: ${request.endringId}, kunne ikke finne endring.",
                    )
                val deltaker = deltakerRepository.get(endring.deltakerId).getOrThrow()
                if (request.relastDeltaker) {
                    Database.transaction {
                        deltakerProducerService.produce(
                            deltaker,
                            forcedUpdate = request.forcedUpdate,
                            publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                            publiserTilDeltakerEksternV1 = request.publiserTilDeltakerEksternV1,
                        )
                    }
                    log.info("Ferdig relastet deltaker ${deltaker.id}")
                }
                distribuerEndringService.produserHendelseFraTiltaksansvarlig(deltaker, endring)
                log.info("Ferdig relastet hendelse med endringId ${request.endringId},")
            }
            call.respond(HttpStatusCode.OK)
        }

        /*
        Brukes til å opprette manglende gjennomføringer hos valp for enkeltplasser.
        Finner de manglende gjennomføringene i db slik:
            SELECT dl.id, dl.prisinformasjon from deltaker d
            join deltakerliste dl on d.deltakerliste_id=dl.id
            join deltaker_status ds on d.id=ds.deltaker_id
            WHERE ds.type='UTKAST_TIL_PAMELDING'
            AND ds.gyldig_til IS NULL
            AND dl.status='KLADD';
         */

        post("/opprett-gjennomforinger-for-enkeltplass") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<OpprettEnkeltplassGjennomforingerInternalRequest>()
            scope.launch {
                log.info(
                    "opprett-gjennomforinger-for-enkeltplass: Starter opprettelse av ${request.gjennomforingIder.size} gjennomføringer",
                )
                request.gjennomforingIder.forEach { gjennomforingId ->
                    val deltaker = deltakerRepository.getEnkeltplassdeltaker(gjennomforingId).getOrThrow()
                    val gjennomforing = deltaker.deltakerliste

                    val vedtak = vedtakRepository.getForDeltaker(deltaker.id)
                        ?: throw IllegalStateException(
                            "Enkeltplass deltaker ${deltaker.id} må ha vedtak for å kunne opprette gjennomføring",
                        )
                    val opprettetAv = navAnsattService.hentEllerOpprettNavAnsatt(vedtak.sistEndretAv).navIdent
                    val ansvarligEnhet = navEnhetService.hentEllerOpprettNavEnhet(vedtak.sistEndretAvEnhet).enhetsnummer

                    gjennomforingRequestProducer.produce(
                        // TODO: Dette blir litt rart for VENTER_PA_OPPSTART
                        GjennomforingRequestPayload.EnkeltplassSoktInn(
                            gjennomforingId = gjennomforing.id,
                            payload = GjennomforingRequestPayload.UpsertEnkeltplass(
                                tiltakskode = gjennomforing.tiltakstype.tiltakskode,
                                // TODO: Hardkodet IngenKostnader
                                prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.IngenKostnader(
                                    aarsak = @Suppress("ktlint:standard:max-line-length")
                                    GjennomforingRequestPayload.Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                                    tilleggsopplysninger = gjennomforing.prisinformasjon,
                                ),
                                organisasjonsnummer = gjennomforing.arrangor?.organisasjonsnummer
                                    ?: throw IllegalStateException("Enkeltplass må ha arrangør med organisasjonsnummer"),
                                ansvarligEnhet = ansvarligEnhet,
                                opprettetAv = opprettetAv,
                                kategorisering = null, // TODO: Skal denne være null?
                            ),
                        ),
                    )
                }
                log.info(
                    "opprett-gjennomforinger-for-enkeltplass: Fullført opprettelse av ${request.gjennomforingIder.size} gjennomføringer",
                )
            }
            call.respond(HttpStatusCode.OK)
        }

        /*
            Brukes for å produsere hendelse til amt-distribusjon i tilfeller hvor manglende transaksjonshåndtering har ført til
            at en deltaker har fått godkjent utkast men handlingen ikke har blitt produsert på topic slik at amt-distribusjon ikke
            får inaktivert oppgave når neste handling blir publisert
            https://trello.com/c/wHea6vGJ/2630-bug-kan-ikke-inaktivere-oppgave-som-om-den-var-en-beskjed
            https://trello.com/c/kxsww0I4/2466-prod-feil-amt-distribusjon-noe-gikk-galt-med-jobb-sendventendevarslerjob
         */
        post("/relast/produser-hendelse-godkjent-utkast") {
            requireInternal(call.request.local.remoteAddress)
            val request = call.receive<ProduserUtkastHendelseRequest>()
            log.info("ProduserUtkast: Produserer hendelse for ${request.deltakere.size} deltakere. DryRun: ${request.dryRun}")
            scope.launch {
                request.deltakere.forEach { deltakerId ->
                    val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
                    val vedtak = vedtakRepository.getForDeltaker(deltakerId)

                    if (vedtak == null) {
                        log.info("ProduserUtkast: Vedtak er ikke opprettet for $deltakerId. Avbryter")
                        return@forEach
                    }
                    if (vedtak.fattet == null) {
                        log.info("ProduserUtkast: Vedtak er ikke fattet for $deltakerId. Avbryter")
                        return@forEach
                    }

                    if (vedtak.fattetAvNav) {
                        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(vedtak.sistEndretAv)
                        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(vedtak.sistEndretAvEnhet)
                        if (request.dryRun) {
                            log.info(
                                "ProduserUtkast: DryRun: Produserer hendelse NavGodkjennUtkast for $deltakerId. status ${deltaker.status.type}",
                            )
                            return@forEach
                        }
                        log.info("ProduserUtkast: Produserer hendelse NavGodkjennUtkast for $deltakerId. status ${deltaker.status.type}")
                        distribuerEndringService.produceHendelseForUtkast(
                            deltaker,
                            navAnsatt,
                            navEnhet,
                        ) { HendelseType.NavGodkjennUtkast(it) }
                        log.info("ProduserUtkast: Done: Produserte hendelse NavGodkjennUtkast for $deltakerId")
                    } else {
                        if (request.dryRun) {
                            log.info("ProduserUtkast: DryRun: Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId")
                            return@forEach
                        }
                        log.info(
                            "ProduserUtkast: Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId. status ${deltaker.status.type}",
                        )
                        distribuerEndringService.hendelseForUtkastGodkjentAvInnbygger(deltaker)
                        log.info("ProduserUtkast: Done: Produserte hendelse InnbyggerGodkjennUtkast for $deltakerId")
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

data class RelastDeltakereRequest(
    val deltakere: List<UUID>,
    val republiserRequest: RepubliserRequest,
)

data class ProduserUtkastHendelseRequest(
    val deltakere: List<UUID>,
    val dryRun: Boolean = false,
)

data class RelastHendelseRequest(
    val endringId: UUID,
    val relastDeltaker: Boolean,
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
    val publiserTilDeltakerEksternV1: Boolean,
)

data class DeleteDeltakereRequest(
    val deltakere: List<UUID>,
)

data class RepubliserRequest(
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
    val publiserTilDeltakerV2: Boolean,
    val publiserTilDeltakerEksternV1: Boolean,
)

data class RepubliserTiltakskoderRequest(
    val tiltakskoder: List<Tiltakskode>,
    val request: RepubliserRequest,
)

data class OpprettEnkeltplassGjennomforingerInternalRequest(
    val gjennomforingIder: List<UUID>,
)

fun isInternal(remoteAdress: String): Boolean = remoteAdress == "127.0.0.1"
