package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.deltaker.bff.apiclients.DtoMappers.toDeltakerOppdatering
import no.nav.amt.deltaker.bff.apiclients.paamelding.PaameldingClient
import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Kladd
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val navBrukerService: NavBrukerService,
    private val paameldingClient: PaameldingClient,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladdForEnkelUtenRamme(
        tiltakskode: Tiltakskode,
        personident: String,
    ) = paameldingClient.opprettKladdEnkelUtenRamme(tiltakskode, personident)

    suspend fun opprettKladd(
        deltakerlisteId: UUID,
        personIdent: String,
    ): Deltaker {
        val eksisterendeDeltaker = deltakerRepository
            .getMany(personIdent, deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker
        }

        val kladdResponse = paameldingClient.opprettKladd(
            personIdent = personIdent,
            deltakerlisteId = deltakerlisteId,
        )

        navBrukerService.upsert(kladdResponse.navBruker)

        Database.transaction {
            deltakerRepository.opprettKladd(kladdResponse)
            deltakerService.lagreDeltakerStatus(kladdResponse.id, kladdResponse.status)
        }

        val deltaker = deltakerRepository.get(kladdResponse.id).getOrThrow()

        MetricRegister.OPPRETTET_KLADD.inc()

        return deltaker
    }

    suspend fun upsertKladd(kladd: Kladd): Deltaker? {
        if (kladd.opprinneligDeltaker.status.type !== DeltakerStatus.Type.KLADD) {
            // Dette kan skje når to brukere er inne på samme deltakelse samtidig
            // eller når samme bruker har flere faner med samme deltakelse
            // eller når nav veileder er så rask med å dele utkast at kladd requesten(som har en delay i frontend) kommer på etterskudd
            log.warn(
                "Kan ikke upserte kladd for deltaker ${kladd.opprinneligDeltaker.id} " +
                    "med status ${kladd.opprinneligDeltaker.status.type}," +
                    "status må være ${DeltakerStatus.Type.KLADD}.",
            )
            return null
        }

        val deltaker = kladd.opprinneligDeltaker.copy(
            deltakelsesinnhold = kladd.pamelding.deltakelsesinnhold,
            bakgrunnsinformasjon = kladd.pamelding.bakgrunnsinformasjon,
            deltakelsesprosent = kladd.pamelding.deltakelsesprosent,
            dagerPerUke = kladd.pamelding.dagerPerUke,
            status = kladd.opprinneligDeltaker.status,
            sistEndret = LocalDateTime.now(),
        )

        Database.transaction {
            deltakerRepository.upsert(deltaker)
            deltakerService.lagreDeltakerStatus(deltaker.id, deltaker.status)
        }

        log.info("Upserted kladd for deltaker med id ${deltaker.id}")

        return deltakerRepository.get(deltaker.id).getOrThrow()
    }

    suspend fun upsertUtkast(utkast: Utkast): Deltaker {
        navEnhetService.hentOpprettEllerOppdaterNavEnhet(utkast.pamelding.endretAvEnhet)
        val deltakeroppdatering = paameldingClient.utkast(utkast).toDeltakerOppdatering()

        deltakerService.oppdaterDeltaker(deltakeroppdatering)
        return deltakerRepository.get(utkast.deltakerId).getOrThrow()
    }

    suspend fun slettKladd(deltaker: Deltaker): Boolean {
        if (deltaker.status.type != DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke slette deltaker med id ${deltaker.id} som har status ${deltaker.status.type}")
            return false
        }
        paameldingClient.slettKladd(deltaker.id)
        Database.transaction {
            deltakerService.deleteDeltaker(deltaker.id)
        }
        return true
    }

    suspend fun avbrytUtkast(
        deltaker: Deltaker,
        avbruttAvEnhet: String,
        avbruttAv: String,
    ) {
        navEnhetService.hentOpprettEllerOppdaterNavEnhet(avbruttAvEnhet)
        paameldingClient.avbrytUtkast(deltaker.id, avbruttAv, avbruttAvEnhet)

        val forrigeDeltaker = deltakerRepository
            .getMany(deltaker.navBruker.personident, deltaker.deltakerliste.id)
            .filter { it.id !== deltaker.id && it.paameldtDato != null }
            .sortedByDescending { it.paameldtDato }
            .firstOrNull() ?: return

        if (forrigeDeltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT &&
            forrigeDeltaker.status.type != DeltakerStatus.Type.AVBRUTT_UTKAST &&
            forrigeDeltaker.status.aarsak?.type != DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
        ) {
            laasOppDeltaker(forrigeDeltaker)
        }
    }

    private fun laasOppDeltaker(deltaker: Deltaker) {
        deltakerRepository.settKanEndres(deltaker.id, true)
        log.info(
            "Har låst opp tidligere deltaker ${deltaker.id} for endringer pga avbrutt utkast på nåværende deltaker",
        )
    }

    fun getKladder(personident: String): List<Deltaker> = deltakerRepository.getMany(personident).filter {
        it.status.type == DeltakerStatus.Type.KLADD
    }
}
