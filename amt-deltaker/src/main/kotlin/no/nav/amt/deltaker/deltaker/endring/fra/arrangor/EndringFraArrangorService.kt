package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerService.Companion.validerIkkeFeilregistrert
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.endring.extensions.endreDeltakersOppstart
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import org.slf4j.LoggerFactory

class EndringFraArrangorService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val hendelseService: HendelseService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun upsertEndretDeltaker(endringFraArrangor: EndringFraArrangor): Deltaker {
        val eksisterendeDeltaker = deltakerRepository.get(endringFraArrangor.deltakerId).getOrThrow()
        validerIkkeFeilregistrert(eksisterendeDeltaker)

        val endretDeltaker = when (endringFraArrangor.endring) {
            is EndringFraArrangor.LeggTilOppstartsdato -> {
                endretDeltaker(eksisterendeDeltaker, endringFraArrangor.endring)
            }
        }

        endretDeltaker.onSuccess { innerDeltaker ->
            return deltakerService.upsertAndProduceDeltaker(
                deltaker = innerDeltaker,
                erDeltakerSluttdatoEndret = eksisterendeDeltaker.sluttdato != innerDeltaker.sluttdato,
                beforeUpsert = { deltaker ->
                    endringFraArrangorRepository.insert(endringFraArrangor)
                    hendelseService.hendelseForEndringFraArrangor(endringFraArrangor, deltaker)
                    deltaker
                },
            )
        }

        endretDeltaker.onFailure {
            log.warn("Endring fra arrangor for deltaker ${eksisterendeDeltaker.id} medfører ingen endring")
        }

        return eksisterendeDeltaker
    }

    private fun endretDeltaker(deltaker: Deltaker, endring: EndringFraArrangor.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraArrangor.LeggTilOppstartsdato -> {
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
}
