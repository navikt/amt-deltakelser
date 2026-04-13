package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.DeltakerUtils.sjekkEndringUtfall
import no.nav.amt.deltaker.deltaker.api.deltaker.getForslagId
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.extensions.oppdaterDeltaker
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.DeltakerProgresjonHandler
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.navtiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerProducerService: DeltakerProducerService,
    private val vedtakRepository: VedtakRepository,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val forslagRepository: ForslagRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun upsertAndProduceDeltaker(
        deltaker: Deltaker,
        erDeltakerSluttdatoEndret: Boolean,
        forceProduce: Boolean? = false,
        nesteStatus: DeltakerStatus? = null,
        beforeUpsert: (Deltaker) -> Deltaker = { it },
        afterUpsert: (Deltaker) -> Unit = { },
    ): Deltaker = transactionalDeltakerUpsert(
        deltaker = deltaker.copy(sistEndret = LocalDateTime.now()),
        erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
        nesteStatus = nesteStatus,
        beforeDeltakerUpsert = beforeUpsert,
        afterDeltakerUpsert = { deltaker ->
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerProducerService.produce(oppdatertDeltaker, forcedUpdate = forceProduce)
            log.info("Oppdatert deltaker ${deltaker.id}")

            afterUpsert(oppdatertDeltaker)
            oppdatertDeltaker
        },
    ).getOrThrow()

    fun deleteDeltaker(deltakerId: UUID) {
        importertFraArenaRepository.deleteForDeltaker(deltakerId)
        vedtakRepository.deleteForDeltaker(deltakerId)
        deltakerEndringRepository.deleteForDeltaker(deltakerId)
        forslagRepository.deleteForDeltaker(deltakerId)
        endringFraArrangorRepository.deleteForDeltaker(deltakerId)
        endringFraTiltakskoordinatorRepository.deleteForDeltaker(deltakerId)
        DeltakerStatusRepository.slettStatus(deltakerId)
        deltakerRepository.slettDeltaker(deltakerId)
    }

    suspend fun feilregistrerDeltaker(deltakerId: UUID) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke feilregistrere deltaker-kladd, id $deltakerId")
            throw IllegalArgumentException("Kan ikke feilregistrere deltaker-kladd")
        }
        upsertAndProduceDeltaker(
            deltaker = deltaker.copy(status = nyDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT)),
            erDeltakerSluttdatoEndret = false,
        )
        log.info("Feilregistrert deltaker med id $deltakerId")
    }

    suspend fun upsertEndretDeltaker(
        deltakerId: UUID,
        endringRequest: EndringRequest,
    ): Deltaker {
        val eksisterendeDeltaker = deltakerRepository.get(deltakerId).getOrThrow()
        validerIkkeFeilregistrert(eksisterendeDeltaker)

        val endring = endringRequest.toEndring()

        val updateResult = endring
            .oppdaterDeltaker(
                deltaker = eksisterendeDeltaker,
                getDeltakelsemengder = { deltakerId -> deltakerHistorikkService.getForDeltaker(deltakerId).toDeltakelsesmengder() },
            ).getOrElse {
                log.warn(
                    "Deltaker ${eksisterendeDeltaker.id} med ${endring.javaClass.simpleName} ikke endret, request skulle ikke blitt sendt",
                )

                // hvis forslag er godkjent og deltaker er uendret
                endringRequest.getForslagId()?.let {
                    deltakerEndringService.godkjennForslagForUendretDeltaker(endringRequest)
                }

                return eksisterendeDeltaker
            }

        log.info("Endret deltaker ${eksisterendeDeltaker.id} med ${endring.javaClass.simpleName}")

        // hent eller opprett Nav-ansatt før transaksjonen starter
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endringRequest.endretAv)

        return upsertAndProduceDeltaker(
            deltaker = updateResult.deltaker,
            erDeltakerSluttdatoEndret = eksisterendeDeltaker.sluttdato != updateResult.deltaker.sluttdato,
            nesteStatus = updateResult.nesteStatus,
            beforeUpsert = { deltaker ->
                deltakerEndringService.upsertEndring(
                    endringRequest = endringRequest,
                    endringResultat = updateResult,
                    endretAvNavAnsatt = navAnsatt,
                )
                deltaker
            },
            afterUpsert = {
                if (endringRequest is ReaktiverDeltakelseRequest) {
                    slettKladdIfExists(updateResult.deltaker)
                }
            },
        )
    }

    private fun slettKladdIfExists(deltaker: Deltaker) {
        deltakerRepository
            .getKladdForDeltakerliste(
                deltakerlisteId = deltaker.deltakerliste.id,
                personident = deltaker.navBruker.personident,
            ).onSuccess { deltaker -> deleteDeltaker(deltaker.id) }
    }

    suspend fun transactionalDeltakerUpsert(
        deltaker: Deltaker,
        erDeltakerSluttdatoEndret: Boolean,
        nesteStatus: DeltakerStatus? = null,
        beforeDeltakerUpsert: (Deltaker) -> Deltaker = { it },
        afterDeltakerUpsert: (Deltaker) -> Deltaker = { it },
    ): Result<Deltaker> = runCatching {
        Database.transaction {
            val deltakerToUpsert = beforeDeltakerUpsert(deltaker)

            deltakerRepository.upsert(deltakerToUpsert)
            lagreDeltakerStatus(
                deltakerId = deltakerToUpsert.id,
                nyDeltakerStatus = deltakerToUpsert.status,
                erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
            )

            nesteStatus?.let {
                DeltakerStatusRepository.lagreStatus(deltakerToUpsert.id, it)
            }

            afterDeltakerUpsert(deltakerToUpsert)
        }
    }

    fun lagreDeltakerStatus(
        deltakerId: UUID,
        nyDeltakerStatus: DeltakerStatus,
        erDeltakerSluttdatoEndret: Boolean,
    ) {
        DeltakerStatusRepository.lagreStatus(deltakerId, nyDeltakerStatus)

        val erNyStatusAktiv = nyDeltakerStatus.gyldigFra.toLocalDate() <= LocalDate.now()

        if (erNyStatusAktiv) {
            DeltakerStatusRepository.deaktiverTidligereStatuser(
                deltakerId = deltakerId,
                excludeStatusId = nyDeltakerStatus.id,
                erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
            )
        } else {
            // Dette skal aldri skje for Arena-deltakelser
            DeltakerStatusRepository.slettTidligereFremtidigeStatuser(deltakerId, nyDeltakerStatus.id)
        }
    }

    private suspend fun upsertSingleDeltaker(
        deltaker: Deltaker,
        endringsType: EndringFraTiltakskoordinator.Endring,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): DeltakerOppdateringResult {
        val endring = EndringFraTiltakskoordinator(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            endring = endringsType,
            endretAv = endretAv.id,
            endretAvEnhet = endretAvEnhet.id,
            endret = LocalDateTime.now(),
        )

        val deltakerToUpdate = sjekkEndringUtfall(deltaker, endring.endring).getOrElse { error ->
            return DeltakerOppdateringResult(deltaker, false, error)
        }

        val oppdatertDeltaker = transactionalDeltakerUpsert(
            deltaker = deltakerToUpdate,
            erDeltakerSluttdatoEndret = (deltaker.sluttdato != deltakerToUpdate.sluttdato),
            afterDeltakerUpsert = {
                endringFraTiltakskoordinatorRepository.insert(listOf(endring))
                if (endringsType is EndringFraTiltakskoordinator.TildelPlass && deltaker.kilde == Kilde.KOMET) {
                    vedtakService.navFattVedtak(deltaker, endretAv, endretAvEnhet)
                }

                val deltakerFromDb = deltakerRepository.get(deltakerToUpdate.id).getOrThrow()

                deltakerProducerService.produce(deltakerFromDb)
                hendelseService.produserHendelseFraTiltaksansvarlig(
                    deltaker = deltakerFromDb,
                    navAnsatt = endretAv,
                    navEnhet = endretAvEnhet,
                    endringsType = endringsType,
                )

                deltakerFromDb
            },
        ).getOrElse { throwable ->
            return DeltakerOppdateringResult(
                deltaker = deltaker,
                isSuccess = false,
                exception = throwable,
            )
        }

        return DeltakerOppdateringResult(
            deltaker = oppdatertDeltaker,
            isSuccess = true,
            exception = null,
        )
    }

    suspend fun oppdaterDeltakere(
        deltakerIder: Set<UUID>,
        endringsType: EndringFraTiltakskoordinator.Endring,
        endretAvIdent: String,
    ): List<DeltakerOppdateringResult> {
        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(endretAvIdent)
        val endretAvNavEnhetId: UUID? = endretAv.navEnhetId

        require(endretAvNavEnhetId != null) { "Tiltakskoordinator ${endretAv.id} mangler en tilknyttet nav-enhet" }

        val endretAvEnhet = navEnhetService.hentEllerOpprettNavEnhet(endretAvNavEnhetId)
        val deltakere = deltakerRepository.getMany(deltakerIder)
        val tiltakskoder = deltakere
            .map { it.deltakerliste.tiltakstype.tiltakskode }
            .distinct()

        require(tiltakskoder.size == 1) { "kan ikke endre på deltakere på flere tiltakskoder samtidig" }
        require(tiltakskoder.first() in Tiltakstype.kursTiltak.plus(Tiltakstype.opplaeringsTiltak)) {
            "kan ikke endre på deltakere på tiltakskoden ${tiltakskoder.first()}"
        }

        return deltakere.map { deltaker ->
            val oppdateringResult = upsertSingleDeltaker(deltaker, endringsType, endretAv, endretAvEnhet)

            if (!oppdateringResult.isSuccess) {
                log.error(
                    "Kunne ikke oppdatere deltaker fra batch: $deltakerIder med endring ${endringsType::class.simpleName}",
                    oppdateringResult.exception,
                )
            }

            oppdateringResult
        }
    }

    suspend fun giAvslag(
        deltakerId: UUID,
        avslag: EndringFraTiltakskoordinator.Avslag,
        endretAv: String,
    ): Deltaker {
        val firstDeltakerOppdateringResult = oppdaterDeltakere(
            deltakerIder = setOf(deltakerId),
            endringsType = avslag,
            endretAvIdent = endretAv,
        ).first()

        return if (firstDeltakerOppdateringResult.isSuccess) {
            firstDeltakerOppdateringResult.deltaker
        } else {
            throw firstDeltakerOppdateringResult.exception!!
        }
    }

    fun produserDeltakereForPerson(
        personident: String,
        publiserTilDeltakerV1: Boolean = true,
        publiserTilDeltakerEksternV1: Boolean = true,
    ): Unit = deltakerRepository.getFlereForPerson(personident).forEach { deltaker ->
        deltakerProducerService.produce(
            deltaker = deltaker,
            publiserTilDeltakerV1 = publiserTilDeltakerV1,
            publiserTilDeltakerEksternV1 = publiserTilDeltakerEksternV1,
        )
    }

    fun oppdaterSistBesokt(
        deltakerId: UUID,
        sistBesokt: ZonedDateTime,
    ) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        hendelseService.hendelseForSistBesokt(deltaker, sistBesokt)
    }

    suspend fun oppdaterDeltakerStatuser() {
        fun getDeltakereSomSkalHaAvsluttendeStatus(): List<Deltaker> = deltakerRepository
            .getDeltakereHvorSluttdatoHarPassert()
            .plus(deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste())
            .distinct()

        fun getDeltakereMedStatusDeltar(): List<Deltaker> = deltakerRepository
            .skalHaStatusDeltar()
            .distinct()
            .map { deltaker -> deltaker.copy(status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR)) }
            .also { log.info("Endret status til DELTAR for ${it.size}") }

        val deltakereSomSkalHaAvsluttendeStatus = getDeltakereSomSkalHaAvsluttendeStatus()

        Database.transaction {
            // avsluttDeltakere burde ha mer finkornede transaksjoner
            avsluttDeltakere(deltakereSomSkalHaAvsluttendeStatus)
        }

        getDeltakereMedStatusDeltar().forEach { deltaker ->
            Database.transaction {
                // kun status er endret, skipper upsert av deltaker
                lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = deltaker.status,
                    erDeltakerSluttdatoEndret = true,
                )

                deltakerProducerService.produce(deltaker)
            }
        }
    }

    fun avsluttDeltakere(deltakereSomSkalAvsluttes: List<Deltaker>) {
        DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(deltakereSomSkalAvsluttes)
            .map { oppdaterVedtakForAvbruttUtkast(it) }
            .forEach { deltaker ->
                val nyStatus = deltaker.status.copy(gyldigFra = LocalDateTime.now())
                deltakerRepository.upsert(deltaker.copy(sistEndret = LocalDateTime.now()))
                lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = nyStatus,
                    erDeltakerSluttdatoEndret = true,
                )

                // henter oppdatert deltaker fra db før publisering på Kafka
                val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
                deltakerProducerService.produce(deltakerFromDb)
            }
    }

    private fun oppdaterVedtakForAvbruttUtkast(deltaker: Deltaker) = if (deltaker.status.type == DeltakerStatus.Type.AVBRUTT_UTKAST) {
        val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker)

        hendelseService.hendelseFraSystem(deltaker) { HendelseType.AvbrytUtkast(it) }
        deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    } else {
        deltaker
    }

    companion object {
        fun validerIkkeFeilregistrert(deltaker: Deltaker) = require(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
            "Kan ikke oppdatere feilregistrert deltaker, id ${deltaker.id}"
        }
    }
}
