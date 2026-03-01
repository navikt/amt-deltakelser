package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
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
    pameldingService: PameldingService,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository,
    vurderingRepository: VurderingRepository,
    hendelseService: HendelseService,
    endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    vedtakRepository: VedtakRepository,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
) {
    val scope = CoroutineScope(Dispatchers.IO)
    val log: Logger = LoggerFactory.getLogger(javaClass)

    fun requireInternal(remoteAddress: String) {
        if (!isInternal(remoteAddress)) throw AuthorizationException("Ikke tilgang til api")
    }

    suspend fun slettDeltaker(deltakerId: UUID) = Database.transaction {
        innsokPaaFellesOppstartRepository.deleteForDeltaker(deltakerId)
        vurderingRepository.deleteForDeltaker(deltakerId)
        deltakerService.deleteDeltaker(deltakerId)
    }

    suspend fun republiserDeltakere(deltakerIder: List<UUID>, request: RepubliserRequest) {
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

    post("/internal/sett-ikke-aktuell/{fra-status}") {
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

    post("/internal/feilregistrer/{deltakerId}") {
        requireInternal(call.request.local.remoteAddress)
        deltakerService.feilregistrerDeltaker(call.getDeltakerId())
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/relast/{deltakerId}") {
        requireInternal(call.request.local.remoteAddress)
        val deltakerId = call.getDeltakerId()
        val request = call.receive<RepubliserRequest>()
        log.info("Relaster deltaker $deltakerId")
        Database.transaction {
            deltakerProducerService.produce(
                deltakerRepository.get(deltakerId).getOrThrow(),
                forcedUpdate = request.forcedUpdate,
                publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                publiserTilDeltakerV2 = request.publiserTilDeltakerV2,
                publiserTilDeltakerEksternV1 = request.publiserTilDeltakerEksternV1,
            )
        }
        log.info("Ferdig relastet deltaker $deltakerId")
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/tving-arena-innlesing") {
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

    post("/internal/relast/tiltakstype/{tiltakskode}") {
        requireInternal(call.request.local.remoteAddress)
        val tiltakskode = Tiltakskode.valueOf(
            call.parameters["tiltakskode"] ?: throw IllegalArgumentException("Tiltakskode ikke satt"),
        )
        val request = call.receive<RepubliserRequest>()
        scope.launch {
            val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
            log.info("Relaster ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
            republiserDeltakere(deltakerIder, request)
            log.info("Ferdig relastet ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/relast/tiltakstyper") {
        requireInternal(call.request.local.remoteAddress)
        val requestBody = call.receive<RepubliserTiltakskoderRequest>()
        scope.launch {
            val tiltakskodeNavn = requestBody.tiltakskoder.map { it.name }
            log.info("Relaster alle deltakere for tiltakskoder $tiltakskodeNavn")
            requestBody.tiltakskoder.forEach { tiltakskode ->
                val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                republiserDeltakere(deltakerIder, requestBody.request)
            }
            log.info("Ferdig relastet alle deltakere for tiltakskoder $tiltakskodeNavn")
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/relast/alle-deltakere") {
        requireInternal(call.request.local.remoteAddress)
        val request = call.receive<RepubliserRequest>()
        scope.launch {
            log.info("Relaster alle deltakere komet er master for")
            for (tiltakskode in Tiltakskode.entries) {
                val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                log.info("Relaster ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
                republiserDeltakere(deltakerIder, request)
                log.info("Ferdig relastet ${deltakerIder.size} deltakere for tiltakskode ${tiltakskode.name}")
            }
            log.info("Ferdig relastet alle deltakere Team Komet er master for")
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/relast/deltakere") {
        requireInternal(call.request.local.remoteAddress)
        val request = call.receive<RelastDeltakereRequest>()
        scope.launch {
            log.info("Relaster ${request.deltakere.size} deltakere")
            republiserDeltakere(request.deltakere, request.republiserRequest)
            log.info("Ferdig relastet ${request.deltakere.size} deltakere")
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/slett-deltakere") {
        requireInternal(call.request.local.remoteAddress)
        if (!Environment.isDev()) throw IllegalStateException("Kan kun slette deltaker i dev")
        val request = call.receive<DeleteDeltakereRequest>()
        scope.launch {
            log.info("Sletter ${request.deltakere.size} deltakere")
            request.deltakere.forEach { deltakerId ->
                deltakerProducerService.tombstone(deltakerId)
                slettDeltaker(deltakerId)
            }
            log.info("Slettet ${request.deltakere.size} deltakere")
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/internal/slett-kladd") {
        requireInternal(call.request.local.remoteAddress)
        val request = call.receive<DeleteDeltakereRequest>()
        scope.launch {
            log.info("Sletter ${request.deltakere.size} deltakere med status KLADD")
            request.deltakere.forEach { deltakerId -> pameldingService.slettKladd(deltakerId) }
            log.info("Slettet ${request.deltakere.size} deltakere med status KLADD")
        }
        call.respond(HttpStatusCode.OK)
    }

    get("internal/avbryt-utkast/{deltakerId}") {
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

    post("/internal/relast/hendelse-fra-tiltakskoordinator") {
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
            hendelseService.produserHendelseFraTiltaksansvarlig(deltaker, endring)
            log.info("Ferdig relastet hendelse med endringId ${request.endringId},")
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
    post("/internal/relast/produser-hendelse-godkjent-utkast") {
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
                    hendelseService.produceHendelseForUtkast(deltaker, navAnsatt, navEnhet) { HendelseType.NavGodkjennUtkast(it) }
                    log.info("ProduserUtkast: Done: Produserte hendelse NavGodkjennUtkast for $deltakerId")
                } else {
                    if (request.dryRun) {
                        log.info("ProduserUtkast: DryRun: Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId")
                        return@forEach
                    }
                    log.info("ProduserUtkast: Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId. status ${deltaker.status.type}")
                    hendelseService.hendelseForUtkastGodkjentAvInnbygger(deltaker)
                    log.info("ProduserUtkast: Done: Produserte hendelse InnbyggerGodkjennUtkast for $deltakerId")
                }
            }
        }
        call.respond(HttpStatusCode.OK)
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

fun isInternal(remoteAdress: String): Boolean = remoteAdress == "127.0.0.1"
