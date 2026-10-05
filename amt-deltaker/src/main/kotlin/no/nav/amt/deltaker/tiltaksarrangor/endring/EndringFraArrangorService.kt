package no.nav.amt.deltaker.tiltaksarrangor.endring

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.veileder.endring.extensions.endreDeltakersOppstart
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Kastes for å avbryte (rulle tilbake) transaksjonen i [DeltakerService.upsertAndProduceDeltaker]
 * når en [EndringFraArrangor] allerede er behandlet tidligere, dvs. en replay av en melding Kafka
 * eller BFF-ens outbox har levert på nytt. Fanges internt i [EndringFraArrangorService] og skal
 * aldri lekke videre til konsumenten.
 */
private class EndringFraArrangorAlleredeBehandlet(
    val endringId: UUID,
) : RuntimeException()

class EndringFraArrangorService(
    private val deltakerService: DeltakerService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val endringFraArrangorBehandletRepository: EndringFraArrangorBehandletRepository,
    private val distribuerEndringService: DistribuerEndringService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsertEndretDeltaker(endringFraArrangor: EndringFraArrangor): Deltaker {
        val eksisterendeDeltaker = deltakerService.getOrThrow(endringFraArrangor.deltakerId)

        // Ignorer kjente duplikater før gjeldende deltakerstatus valideres. Innsettingen i
        // beforeUpsert er fortsatt den transaksjonelle sikringen mot samtidige leveranser.
        if (endringFraArrangorBehandletRepository.exists(endringFraArrangor.id)) {
            log.info(
                "Endring fra arrangør ${endringFraArrangor.id} for deltaker ${eksisterendeDeltaker.id} " +
                    "er allerede behandlet, ignorerer replay",
            )
            return eksisterendeDeltaker
        }

        DeltakerService.validerIkkeFeilregistrert(eksisterendeDeltaker)

        val endretDeltaker = when (endringFraArrangor.endring) {
            is EndringFraArrangor.LeggTilOppstartsdato ->
                endretDeltaker(eksisterendeDeltaker, endringFraArrangor.endring)
        }

        endretDeltaker.onSuccess { innerDeltaker ->
            try {
                return deltakerService.upsertAndProduceDeltaker(
                    deltaker = innerDeltaker,
                    erDeltakerSluttdatoEndret = eksisterendeDeltaker.sluttdato != innerDeltaker.sluttdato,
                    beforeUpsert = { deltaker ->
                        // Unikhetskontrollen sikrer at samtidige duplikate leveranser bare behandles én gang.
                        if (!endringFraArrangorBehandletRepository.markerSomBehandlet(
                                endringFraArrangor.id,
                                endringFraArrangor.deltakerId,
                            )
                        ) {
                            throw EndringFraArrangorAlleredeBehandlet(endringFraArrangor.id)
                        }
                        endringFraArrangorRepository.insert(endringFraArrangor)
                        distribuerEndringService.hendelseForEndringFraArrangor(endringFraArrangor, deltaker)
                        deltaker
                    },
                )
            } catch (alleredeBehandlet: EndringFraArrangorAlleredeBehandlet) {
                log.info(
                    "Endring fra arrangør ${alleredeBehandlet.endringId} for deltaker ${eksisterendeDeltaker.id} " +
                        "er allerede behandlet, ignorerer replay",
                )
                return eksisterendeDeltaker
            }
        }

        endretDeltaker.onFailure {
            // Registrer meldingen selv om deltakeren er uendret, så den ikke behandles på nytt.
            Database.transaction {
                endringFraArrangorBehandletRepository.markerSomBehandlet(
                    endringFraArrangor.id,
                    endringFraArrangor.deltakerId,
                )
            }
            log.info(
                "Endring fra arrangør ${endringFraArrangor.id} for deltaker ${eksisterendeDeltaker.id} " +
                    "var allerede gjeldende og er registrert som behandlet",
            )
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
