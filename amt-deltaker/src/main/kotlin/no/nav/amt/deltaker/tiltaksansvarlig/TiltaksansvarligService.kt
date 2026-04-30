package no.nav.amt.deltaker.tiltaksansvarlig

import no.nav.amt.deltaker.api.tiltaksansvarlig.DeltakerOppdateringResult
import no.nav.amt.deltaker.innbygger.DistribuerEndringService
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.DeltakerUtils.sjekkEndringUtfall
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class TiltaksansvarligService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerProducerService: DeltakerProducerService,
    private val distribuerEndringService: DistribuerEndringService,
    private val vedtakService: VedtakService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    private fun upsertSingleDeltaker(
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

        val oppdatertDeltaker = deltakerService
            .transactionalDeltakerUpsert(
                deltaker = deltakerToUpdate,
                erDeltakerSluttdatoEndret = (deltaker.sluttdato != deltakerToUpdate.sluttdato),
                afterDeltakerUpsert = {
                    endringFraTiltakskoordinatorRepository.insert(listOf(endring))
                    if (endringsType is EndringFraTiltakskoordinator.TildelPlass && deltaker.kilde == Kilde.KOMET) {
                        vedtakService.navFattVedtak(deltaker, endretAv, endretAvEnhet)
                    }

                    val deltakerFromDb = deltakerRepository.get(deltakerToUpdate.id).getOrThrow()

                    deltakerProducerService.produce(deltakerFromDb)
                    distribuerEndringService.produserHendelseFraTiltaksansvarlig(
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
}
