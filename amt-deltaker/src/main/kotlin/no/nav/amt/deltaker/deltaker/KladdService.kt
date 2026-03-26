package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerInsertDbo
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerUpdateDbo
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.GjennomforingInsertDbo
import no.nav.amt.deltaker.deltakerliste.GjennomforingKladdUpdateDbo
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
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
    ): Deltaker {
        val navBruker = navBrukerService.get(personident).getOrThrow()
        val tiltak = Tiltakskode.valueOf(tiltakskode.name).let {
            tiltakRepository.get(tiltakskode).getOrThrow()
        }
        val gjennomforing = GjennomforingInsertDbo(
            id = UUID.randomUUID(),
            type = GjennomforingType.Enkeltplass,
            tiltakId = tiltak.id,
            navn = tiltak.navn,
            status = GjennomforingStatusType.KLADD,
            // Antagelig ubetydelig, men kan ha noe å si for hva som skjer når vi evt leser gjennomføringen igjen fra valp
            oppstart = Oppstartstype.LOPENDE,
            apentForPamelding = true,
            pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        val kladd = lagEnkeltplassKladdInsertDbo(
            navBruker.personId,
            gjennomforing.id,
            tiltak,
        )

        Database.transaction {
            deltakerListeRepository.upsert(gjennomforing)
            deltakerRepository.upsert(kladd)
            DeltakerStatusRepository.lagreStatus(kladd.id, nyDeltakerStatus(DeltakerStatus.Type.KLADD))
        }

        return deltakerRepository.get(kladd.id).getOrThrow()
    }

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        startdato: LocalDate?,
        sluttdato: LocalDate?,
        beskrivelse: String?,
        prisinformasjon: String?,
    ): Deltaker {
        // Trenger egentlig bare deltakeren for tiltakstypen sånn at ledeteksten
        // kan puttes i jsonobjektet i innhold
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
            "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id $deltakerId har status ${deltaker.status.type}"
        }

        val gjennomforingUpdateDbo = GjennomforingKladdUpdateDbo(
            id = deltaker.deltakerliste.id,
            prisinformasjon = prisinformasjon,
        )
        val kladdUpdateDbo = lagEnkeltplassKladdUpdateDbo(
            deltakerId = deltakerId,
            tiltakstype = deltaker.deltakerliste.tiltakstype,
            startdato = startdato,
            sluttdato = sluttdato,
            beskrivelse = beskrivelse,
        )
        Database.transaction {
            deltakerListeRepository.update(gjennomforingUpdateDbo)
            deltakerRepository.update(kladdUpdateDbo)
        }
        return deltakerRepository.get(deltakerId).getOrThrow()
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

        fun lagEnkeltplassKladdInsertDbo(
            navBrukerId: UUID,
            deltakerlisteId: UUID,
            tiltakstype: Tiltakstype,
        ) = DeltakerInsertDbo(
            id = UUID.randomUUID(),
            navBrukerId = navBrukerId,
            deltakerlisteId = deltakerlisteId,
            startdato = null,
            sluttdato = null,
            dagerPerUke = null,
            deltakelsesprosent = null,
            bakgrunnsinformasjon = null,
            deltakelsesinnhold = Deltakelsesinnhold(tiltakstype.innhold?.ledetekst, emptyList()),
            vedtaksinformasjon = null,
            sistEndret = LocalDateTime.now(),
            kilde = Kilde.KOMET,
            erManueltDeltMedArrangor = false,
        )

        fun lagEnkeltplassKladdUpdateDbo(
            deltakerId: UUID,
            tiltakstype: Tiltakstype,
            startdato: LocalDate?,
            sluttdato: LocalDate?,
            beskrivelse: String?,
        ) = DeltakerUpdateDbo(
            id = deltakerId,
            startdato = startdato,
            sluttdato = sluttdato,
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = tiltakstype.innhold?.ledetekst,
                innhold = beskrivelse?.let {
                    listOf(Innhold.createFritekstInnhold(beskrivelse))
                } ?: emptyList(),
            ),
        )
    }
}
