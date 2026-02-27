package no.nav.amt.deltaker.bff.testdata

import kotlinx.coroutines.delay
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Pamelding
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.deltaker.bff.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.toInnhold
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val TESTVEILEDER = "Z990098"
const val TESTENHET = "0314"

// Plutselig Lagsport
const val TESTARRANGORANSATT = "fff9a665-cbde-4dbc-9ef9-deb8681a0d6f"

class TestdataService(
    private val pameldingService: PameldingService,
    private val deltakerlisteService: DeltakerlisteService,
    private val arrangorMeldingProducer: ArrangorMeldingProducer,
    private val deltakerRepository: DeltakerRepository,
) {
    suspend fun opprettDeltakelse(opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest): Deltaker {
        deltakerFinnesAllerede(opprettTestDeltakelseRequest)
        val deltakerliste = deltakerlisteService.get(opprettTestDeltakelseRequest.deltakerlisteId).getOrThrow()
        val forventetSluttdato = opprettTestDeltakelseRequest.startdato.plusMonths(3)
        valider(
            startdato = opprettTestDeltakelseRequest.startdato,
            sluttdato = forventetSluttdato,
            deltakerliste = deltakerliste,
        )
        val deltaker = pameldingService.opprettDeltaker(
            deltakerlisteId = opprettTestDeltakelseRequest.deltakerlisteId,
            personIdent = opprettTestDeltakelseRequest.personident,
        )
        val deltakerId = deltaker.id

        delay(10)

        val utkast = lagUtkast(deltakerId, deltakerliste, opprettTestDeltakelseRequest)
        pameldingService.upsertUtkast(utkast)

        delay(100)

        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltakerId,
            startdato = opprettTestDeltakelseRequest.startdato,
            sluttdato = forventetSluttdato,
        )

        Database.transaction {
            arrangorMeldingProducer.produce(endringFraArrangor)
        }

        delay(100)

        return deltakerRepository.get(deltakerId).getOrThrow()
    }

    private fun deltakerFinnesAllerede(opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest) {
        val eksisterendeDeltaker = deltakerRepository
            .getMany(opprettTestDeltakelseRequest.personident, opprettTestDeltakelseRequest.deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            throw IllegalArgumentException("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
        }
    }

    companion object {
        fun lagUtkast(
            deltakerId: UUID,
            deltakerliste: Deltakerliste,
            opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest,
        ) = Utkast(
            deltakerId = deltakerId,
            pamelding = Pamelding(
                deltakelsesinnhold = lagInnhold(deltakerliste),
                bakgrunnsinformasjon = null,
                deltakelsesprosent = opprettTestDeltakelseRequest.deltakelsesprosent.toFloat(),
                dagerPerUke = opprettTestDeltakelseRequest.dagerPerUke?.toFloat(),
                endretAv = TESTVEILEDER,
                endretAvEnhet = TESTENHET,
            ),
            godkjentAvNav = true,
        )

        private fun valider(
            startdato: LocalDate,
            sluttdato: LocalDate,
            deltakerliste: Deltakerliste,
        ) {
            if (deltakerliste.tiltak.tiltakskode != Tiltakskode.ARBEIDSFORBEREDENDE_TRENING) {
                throw IllegalArgumentException("Det er kun AFT som er støttet for testdeltakelser inntil videre")
            }
            if (startdato.isBefore(deltakerliste.startDato)) {
                throw IllegalArgumentException("Kan ikke sette startdato tidligere enn gjennomføringens startdato")
            }
            if (deltakerliste.sluttDato != null && sluttdato.isAfter(deltakerliste.sluttDato)) {
                throw IllegalArgumentException("Kan ikke sette sluttdato senere enn gjennomføringens sluttdato")
            }
        }

        private fun lagEndringFraArrangor(
            deltakerId: UUID,
            startdato: LocalDate,
            sluttdato: LocalDate,
        ) = EndringFraArrangor(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            opprettetAvArrangorAnsattId = UUID.fromString(TESTARRANGORANSATT),
            opprettet = LocalDateTime.now(),
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        private fun lagInnhold(deltakerliste: Deltakerliste): Deltakelsesinnhold {
            val innhold = deltakerliste.tiltak.innhold
            val valgtInnhold = innhold?.innholdselementer?.firstOrNull()?.toInnhold(valgt = true)
            return Deltakelsesinnhold(
                ledetekst = innhold?.ledetekst,
                innhold = valgtInnhold?.let { listOf(it) } ?: emptyList(),
            )
        }
    }
}
