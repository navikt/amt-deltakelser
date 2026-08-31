package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.job.DeltakerProgresjonHandler
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val deltakerProducerService: DeltakerProducerService,
    private val vedtakRepository: VedtakRepository,
    private val vedtakService: VedtakService,
    private val distribuerEndringService: DistribuerEndringService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val forslagRepository: ForslagRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun transactionalDeltakerUpsert(
        deltaker: Deltaker,
        erDeltakerSluttdatoEndret: Boolean,
        nesteStatus: DeltakerStatus? = null,
        beforeDeltakerUpsert: (Deltaker) -> Deltaker = { it },
        afterDeltakerUpsert: (Deltaker) -> Deltaker = { it },
    ): Result<Deltaker> = runCatching {
        Database.transaction {
            val deltakerToUpsert = beforeDeltakerUpsert(deltaker)

            deltakerRepository.upsert(deltakerToUpsert)
            val gjeldendeDeltakerStatus = lagreDeltakerStatus(
                deltakerId = deltakerToUpsert.id,
                nyDeltakerStatus = deltakerToUpsert.status,
                erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
            )

            nesteStatus?.let {
                DeltakerStatusRepository.lagreStatus(deltakerToUpsert.id, it)
            }

            afterDeltakerUpsert(
                deltakerToUpsert.copy(status = gjeldendeDeltakerStatus),
            )
        }
    }

    fun upsertAndProduceDeltaker(
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

    fun feilregistrerDeltaker(deltakerId: UUID) {
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

    fun lagreDeltakerStatus(
        deltakerId: UUID,
        nyDeltakerStatus: DeltakerStatus,
        erDeltakerSluttdatoEndret: Boolean,
    ): DeltakerStatus {
        val eksisterendeStatus = DeltakerStatusRepository.getGjeldendeDeltakerStatus(deltakerId)
        val statusErUendret = eksisterendeStatus?.harLiktInnholdSom(nyDeltakerStatus) == true

        val gjeldendeStatus = if (statusErUendret) {
            log.info("Ny deltakerstatus for deltaker $deltakerId er lik eksisterende status, hopper over insert")
            eksisterendeStatus
        } else {
            DeltakerStatusRepository.lagreStatus(deltakerId, nyDeltakerStatus)
            nyDeltakerStatus
        }

        val erInnkommendeStatusAktiv = nyDeltakerStatus.gyldigFra.toLocalDate() <= LocalDate.now()

        if (erInnkommendeStatusAktiv) {
            DeltakerStatusRepository.deaktiverTidligereStatuser(
                deltakerId = deltakerId,
                excludeStatusId = gjeldendeStatus.id,
                erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
            )
        } else {
            // Dette skal aldri skje for Arena-deltakelser
            DeltakerStatusRepository.slettTidligereFremtidigeStatuser(
                deltakerId = deltakerId,
                excludeStatusId = gjeldendeStatus.id,
            )
        }

        return gjeldendeStatus
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
        distribuerEndringService.hendelseForSistBesokt(deltaker, sistBesokt)
    }

    fun oppdaterDeltakerStatuser() {
        val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository
            .getDeltakereHvorSluttdatoHarPassert()
            .plus(deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste())
            .distinct()

        Database.transaction {
            // avsluttDeltakere burde ha mer finkornede transaksjoner
            avsluttDeltakere(deltakereSomSkalHaAvsluttendeStatus)
        }

        val deltakereMedStatusDeltar = deltakerRepository.skalHaStatusDeltar().distinct()

        var antallOppdatert = 0
        deltakereMedStatusDeltar.forEach { deltaker ->
            runCatching {
                Database.transaction {
                    val nyStatus = nyDeltakerStatus(DeltakerStatus.Type.DELTAR)

                    // kun status er endret, skipper upsert av deltaker
                    val gjeldendeStatus = lagreDeltakerStatus(
                        deltakerId = deltaker.id,
                        nyDeltakerStatus = nyStatus,
                        erDeltakerSluttdatoEndret = true,
                    )

                    deltakerProducerService.produce(deltaker.copy(status = gjeldendeStatus))
                }
            }.onSuccess {
                antallOppdatert++
            }.onFailure { e ->
                log.error("Feil ved oppdatering av deltaker ${deltaker.id} til DELTAR", e)
            }
        }

        log.info("Endret status til DELTAR for $antallOppdatert av ${deltakereMedStatusDeltar.size}")
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

        distribuerEndringService.hendelseFraSystem(deltaker) { HendelseType.AvbrytUtkast(it) }
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
