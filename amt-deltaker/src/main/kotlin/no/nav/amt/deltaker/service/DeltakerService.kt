package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.getForslagId
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.job.DeltakerProgresjonHandler
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.deltaker.veileder.endring.extensions.anvendPaaDeltaker
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
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
    private val distribuerEndringService: DistribuerEndringService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val forslagRepository: ForslagRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
    private val unleashToggle: CommonUnleashToggle,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    suspend fun upsertEndretDeltaker(
        deltakerId: UUID,
        endringRequest: EndringRequest,
    ): Deltaker {
        val eksisterendeDeltaker = deltakerRepository.get(deltakerId).getOrThrow()
        validerIkkeFeilregistrert(eksisterendeDeltaker)

        require(unleashToggle.erKometMasterForTiltakstype(eksisterendeDeltaker.deltakerliste.tiltakstype.tiltakskode)) {
            "Kan ikke utføre endring på deltaker $deltakerId på tiltakstype ${eksisterendeDeltaker.deltakerliste.tiltakstype.tiltakskode} som komet ikke eier"
        }

        require(eksisterendeDeltaker.navBruker.harAktivOppfolgingsperiode || endringRequest.kanIverksettesUtenAktivOppfolging()) {
            "Kan ikke utføre endring ${endringRequest.javaClass.simpleName} på deltaker $deltakerId uten aktiv oppfølgingsperiode"
        }
        val endring = endringRequest.toEndring(eksisterendeDeltaker.deltakerliste.tiltakstype)

        val updateResult = endring
            .anvendPaaDeltaker(
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
