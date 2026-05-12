package no.nav.amt.deltaker.veileder

import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.KodeverkValgRepository
import no.nav.amt.deltaker.repository.dbo.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class KladdService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(
        deltakerListeId: UUID,
        personIdent: String,
    ): Deltaker {
        val eksisterendeDeltaker = deltakerRepository
            .getFlereForPerson(personIdent, deltakerListeId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker
        }

        return deltakerService
            .upsertAndProduceDeltaker(
                deltaker = lagKladd(
                    navBrukerService.get(personIdent).getOrThrow(),
                    deltakerlisteRepository.get(deltakerListeId).getOrThrow(),
                ),
                erDeltakerSluttdatoEndret = false,
            ).also { deltaker ->
                log.info("Lagret kladd for deltaker med id ${deltaker.id}")
            }
    }

    fun oppdaterKladd(
        deltaker: Deltaker,
        innhold: List<Innhold>,
        bakgrunnsinformasjon: String?,
        deltakelsesprosent: Int?,
        dagerPerUke: Int?,
    ): Deltaker {
        val kladdUpsertDbo = lagKladdUpsertDbo(
            deltaker = deltaker,
            innhold = innhold,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesprosent = deltakelsesprosent,
            dagerPerUke = dagerPerUke,
        )

        deltakerRepository.upsertKladd(kladdUpsertDbo)

        return deltakerRepository.get(deltaker.id).getOrThrow()
    }

    fun slettKladd(deltakerId: UUID) {
        deltakerRepository.get(deltakerId).onSuccess { opprinneligDeltaker ->
            val gjennomforingId = opprinneligDeltaker.deltakerliste.id
            if (opprinneligDeltaker.status.type != DeltakerStatus.Type.KLADD) {
                log.warn("Kan ikke slette deltaker med id $deltakerId som har status ${opprinneligDeltaker.status.type}")
                throw IllegalArgumentException(
                    "Kan ikke slette deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
                )
            }

            if (opprinneligDeltaker.erEnkeltplass) {
                require(!opprinneligDeltaker.deltakerliste.erDeltMedValp) {
                    "Kan ikke slette Enkeltplass gjennomføring $gjennomforingId som er delt med valp"
                }
            }

            Database.transaction {
                deltakerService.deleteDeltaker(deltakerId)
                if (opprinneligDeltaker.erEnkeltplass) {
                    log.info("Sletter deltakerliste med id $gjennomforingId for kladd deltaker med id $deltakerId")
                    KodeverkValgRepository.deleteForGjennomforing(gjennomforingId)
                    deltakerlisteRepository.delete(gjennomforingId)
                }
            }
        }
    }

    companion object {
        private fun lagKladd(
            navBruker: NavBruker,
            deltakerListe: Deltakerliste,
        ) = Deltaker(
            id = UUID.randomUUID(),
            navBruker = navBruker,
            deltakerliste = deltakerListe,
            startdato = null,
            sluttdato = null,
            dagerPerUke = null,
            deltakelsesprosent = null,
            bakgrunnsinformasjon = null,
            deltakelsesinnhold = Deltakelsesinnhold(deltakerListe.tiltakstype.innhold?.ledetekst, emptyList()),
            status = DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            sistEndret = LocalDateTime.now(),
            kilde = Kilde.KOMET,
            erManueltDeltMedArrangor = false,
            opprettet = LocalDateTime.now(),
        )

        fun lagKladdUpsertDbo(
            deltaker: Deltaker,
            innhold: List<Innhold>,
            bakgrunnsinformasjon: String?,
            deltakelsesprosent: Int?,
            dagerPerUke: Int?,
        ) = DeltakerKladdUpsertDbo(
            id = deltaker.id,
            navBrukerId = deltaker.navBruker.personId,
            deltakerlisteId = deltaker.deltakerliste.id,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesprosent = deltakelsesprosent?.toFloat(),
            dagerPerUke = dagerPerUke?.toFloat(),
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                    ?.ledetekst,
                innhold = innhold,
            ),
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        )
    }
}
