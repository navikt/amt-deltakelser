package no.nav.amt.deltaker.tiltaksarrangor.endring

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.veileder.endring.extensions.endreDeltakersOppstart
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import org.slf4j.LoggerFactory

class EndringFraArrangorService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val distribuerEndringService: DistribuerEndringService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsertEndretDeltaker(endringFraArrangor: EndringFraArrangor): Deltaker {
        val eksisterendeDeltaker = deltakerRepository.get(endringFraArrangor.deltakerId).getOrThrow()
        DeltakerService.validerIkkeFeilregistrert(eksisterendeDeltaker)

        val endretDeltaker = when (endringFraArrangor.endring) {
            is EndringFraArrangor.LeggTilOppstartsdato ->
                endretDeltaker(eksisterendeDeltaker, endringFraArrangor.endring)
        }

        endretDeltaker.onSuccess { innerDeltaker ->
            return deltakerService.upsertAndProduceDeltaker(
                deltaker = innerDeltaker,
                erDeltakerSluttdatoEndret = eksisterendeDeltaker.sluttdato != innerDeltaker.sluttdato,
                beforeUpsert = { deltaker ->
                    endringFraArrangorRepository.insert(endringFraArrangor)
                    distribuerEndringService.hendelseForEndringFraArrangor(endringFraArrangor, deltaker)
                    deltaker
                },
            )
        }

        endretDeltaker.onFailure {
            log.warn("Endring fra arrangor for deltaker ${eksisterendeDeltaker.id} medfører ingen endring")
        }

        return eksisterendeDeltaker
    }

    private fun endretDeltaker(
        deltaker: Deltaker,
        endring: EndringFraArrangor.Endring,
    ): Result<Deltaker> {
        fun endreDeltaker(
            erEndret: Boolean,
            block: () -> Deltaker,
        ) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraArrangor.LeggTilOppstartsdato ->
                endreDeltaker(deltaker.startdato != endring.startdato) {
                    deltaker.endreDeltakersOppstart(
                        startdato = endring.startdato,
                        sluttdato = endring.sluttdato,
                        deltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder(),
                    )
                }
        }
    }
}
