package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.GjennomforingInsertDbo
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class KladdService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerListeRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val tiltakRepository: TiltakstypeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(
        tiltakskode: Tiltakskode,
        personident: String,
    ): UUID {
        val tiltak = Tiltakskode.valueOf(tiltakskode.name).let {
            tiltakRepository.get(tiltakskode).getOrThrow()
        }
        val gjennomforing = GjennomforingInsertDbo(
            id = UUID.randomUUID(),
            type = GjennomforingType.Enkeltplass,
            tiltakId = tiltak.id,
            navn = tiltak.navn, // TODO: Skal vi sette et navn som passer for omgivelsene?
            status = GjennomforingStatusType.KLADD,
            // Antagelig ubetydelig, men kan ha noe å si for hva som skjer når vi evt leser gjennomføringen igjen fra valp
            oppstart = Oppstartstype.LOPENDE,
            apentForPamelding = true,
            // arrangor = null, // TODO: Denne er ikke nullable i databasen
            pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        val kladd = lagKladd(
            navBrukerService.get(personident).getOrThrow(),
            deltakerListeRepository.get(gjennomforing.id).getOrThrow(),
        )

        Database.transaction {
            deltakerListeRepository.upsert(gjennomforing)
            deltakerRepository.upsert(kladd)
        }
        return kladd.id
    }

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
                    deltakerListeRepository.get(deltakerListeId).getOrThrow(),
                ),
                erDeltakerSluttdatoEndret = false,
            ).also { deltaker ->
                log.info("Lagret kladd for deltaker med id ${deltaker.id}")
            }
    }

    suspend fun slettKladd(deltakerId: UUID) {
        deltakerRepository.get(deltakerId).onSuccess { opprinneligDeltaker ->
            if (opprinneligDeltaker.status.type != DeltakerStatus.Type.KLADD) {
                log.warn("Kan ikke slette deltaker med id $deltakerId som har status ${opprinneligDeltaker.status.type}")
                throw IllegalArgumentException(
                    "Kan ikke slette deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
                )
            }
            Database.transaction {
                deltakerService.deleteDeltaker(deltakerId)
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
            status = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            sistEndret = LocalDateTime.now(),
            kilde = Kilde.KOMET,
            erManueltDeltMedArrangor = false,
            opprettet = LocalDateTime.now(),
        )
    }
}
