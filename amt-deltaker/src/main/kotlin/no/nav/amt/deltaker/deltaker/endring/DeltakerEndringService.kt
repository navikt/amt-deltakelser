package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.deltaker.getForslagId
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val hendelseService: HendelseService,
    private val forslagService: ForslagService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun godkjennForslagForUendretDeltaker(endringRequest: EndringRequest) {
        endringRequest.getForslagId()?.let { forslagId ->
            val ansatt = navAnsattRepository.getOrThrow(endringRequest.endretAv)
            val enhet = navEnhetRepository.getOrThrow(endringRequest.endretAvEnhet)

            Database.transaction {
                forslagService.godkjennForslag(
                    forslagId = forslagId,
                    godkjentAvAnsattId = ansatt.id,
                    godkjentAvEnhetId = enhet.id,
                )
            }
        }
    }

    fun upsertEndring(endringRequest: EndringRequest, endringResultat: VellykketEndring): DeltakerEndring {
        val ansatt = navAnsattRepository.getOrThrow(endringRequest.endretAv)
        val enhet = navEnhetRepository.getOrThrow(endringRequest.endretAvEnhet)

        val godkjentForslag = endringRequest.getForslagId()?.let { forslagId ->
            forslagService.godkjennForslag(
                forslagId = forslagId,
                godkjentAvAnsattId = ansatt.id,
                godkjentAvEnhetId = enhet.id,
            )
        }

        val deltakerEndring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = endringResultat.deltaker.id,
            endring = endringRequest.toEndring(),
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
            endret = LocalDateTime.now(),
            forslag = godkjentForslag,
        )

        val behandletTidspunkt = if (endringResultat.erFremtidigEndring) null else LocalDateTime.now()

        deltakerEndringRepository.upsert(
            deltakerEndring = deltakerEndring,
            behandletTidspunkt = behandletTidspunkt,
        )

        hendelseService.hendelseForDeltakerEndring(
            deltakerEndring = deltakerEndring,
            deltaker = endringResultat.deltaker,
            navAnsatt = ansatt,
            navEnhet = enhet,
        )

        return deltakerEndring
    }

    fun behandleLagretDeltakelsesmengde(deltakerEndring: DeltakerEndring, deltaker: Deltaker): Result<VellykketEndring> {
        val deltakelsesmengde = deltakerEndring.toDeltakelsesmengde()
            ?: throw IllegalStateException("Endring ${deltakerEndring.id} er ikke en EndreDeltakelsesmengde")

        val gyldigeDeltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()

        val endringenErIkkeUtfort = deltaker.deltakelsesprosent != deltakelsesmengde.deltakelsesprosent ||
            deltaker.dagerPerUke != deltakelsesmengde.dagerPerUke

        val logMessage = "Behandler endring: ${deltakerEndring.id}, deltaker: ${deltaker.id}"

        val utfall =
            if (deltakelsesmengde == gyldigeDeltakelsesmengder.gjeldende && endringenErIkkeUtfort) {
                log.info("$logMessage, utfall: Vellykket")
                Result.success(
                    VellykketEndring(
                        deltaker.copy(
                            deltakelsesprosent = deltakelsesmengde.deltakelsesprosent,
                            dagerPerUke = deltakelsesmengde.dagerPerUke,
                        ),
                    ),
                )
            } else {
                log.info("$logMessage, utfall: Ikke vellykket")
                Result.failure(IllegalStateException("Endringen er ikke lenger gyldig"))
            }

        deltakerEndringRepository.upsert(deltakerEndring, LocalDateTime.now())

        return utfall
    }
}
