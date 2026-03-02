package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.tiltakskoordinator.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerLaaseSvcLegacy(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerLaaseSvc: DeltakerLaaseSvc,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // benyttes av DeltakerV2Consumer
    fun oppdaterDeltakerLaas(
        deltakerId: UUID,
        personident: String,
        deltakerlisteId: UUID,
    ) {
        /*
            Denne funksjonen er ment til alle scenarioer hvor det er relevant med låsing av deltakere.
            Skal kalles etter at databasen er oppdatert med ny/oppdatering av deltaker som gjør at den er vanskelig å gjenbruke noen steder
            (flere steder så oppdateres ikke databasen med deltakerendringer før resultatet er mottatt på kafka).
            OBS: Flere deltakelser kan ha samme påmeldt dato(i tilfelle den ene er historisert).
            Scenario 1: oppdatering av eksisterende deltakelser
            Scenario 2: Import av data fra Arena
            Scenario 3: Avbryt utkast som i praksis vil ha en deltakelse uten påmeldtdato
         */
        val deltakelserPaaPerson = deltakerRepository
            .getFlereForPerson(personident, deltakerlisteId)
            .sortedWith(
                compareByDescending<Deltaker> { deltakerLaaseSvc.getPameldTidspunkt(it) }
                    .thenByDescending { it.status.gyldigFra },
            )

        if (deltakelserPaaPerson.none { it.id == deltakerId }) {
            throw IllegalStateException("Den nye deltakelsen $deltakerId må være upsertet for å bruke denne funksjonen")
        }

        val nyesteDeltakelse = deltakelserPaaPerson
            .firstOrNull { it.status.type in AKTIVE_STATUSER }
            ?: deltakelserPaaPerson.first()

        if (deltakerId != nyesteDeltakelse.id) {
            log.info("Fikk oppdatering på $deltakerId som skal låses fordi det er nyere deltakelse ${nyesteDeltakelse.id} på personen")
        }

        val deltakelserSomSkalLaases = deltakelserPaaPerson
            .filter { it.kanEndres }
            .filter {
                it.id != nyesteDeltakelse.id ||
                    nyesteDeltakelse.status.type == DeltakerStatus.Type.FEILREGISTRERT ||
                    nyesteDeltakelse.status.aarsak?.type == DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            }

        val laasesMedAktivStatus = deltakelserSomSkalLaases
            .filter { it.status.type in AKTIVE_STATUSER }

        if (laasesMedAktivStatus.any()) {
            throw IllegalStateException(
                "ugyldig state. Fant eldre deltakelser med aktiv status: " +
                    "Nyeste deltaker ${nyesteDeltakelse.id} " +
                    // TODO "påmeldt ${nyesteDeltakelse.paameldtDato} " +
                    "har status ${nyesteDeltakelse.status.type}. " +
                    "Eldre deltakelse(r) ${laasesMedAktivStatus.map { it.id }} " +
                    // TODO "påmeldt ${laasesMedAktivStatus.map { it.paameldtDato }} " +
                    "har status ${laasesMedAktivStatus.map { it.status.type }}. ",
            )
        }

        if (!nyesteDeltakelse.kanEndres) {
            // Dette skal ikke skje i en ventet funksjonell flyt men mange feil med
            // låsing opp igjennom tidene har ført til at nyeste deltakelse er låst
            log.info("Nyeste deltakelse ${nyesteDeltakelse.id} var låst for endringer. Låser opp")
            deltakerRepository.settKanEndres(nyesteDeltakelse.id, true)
        }

        if (deltakelserSomSkalLaases.any()) {
            laasSingleOrMultipleDeltakelser(
                iderSomSkalLaases = deltakelserSomSkalLaases.map { it.id }.toSet(),
                nyDeltakerId = nyesteDeltakelse.id,
            )
        }
    }

    // skal kalles i oppdaterDeltaker
    fun laasTidligereDeltakelser(deltakeroppdatering: Deltakeroppdatering) {
        if (deltakeroppdatering.status.type in AKTIVE_STATUSER && harEndretStatus(deltakeroppdatering)) {
            val tidligereDeltakelser = deltakerRepository.getTidligereAvsluttedeDeltakelser(deltakeroppdatering.id)

            if (tidligereDeltakelser.any()) {
                laasSingleOrMultipleDeltakelser(
                    iderSomSkalLaases = tidligereDeltakelser.toSet(),
                    nyDeltakerId = deltakeroppdatering.id,
                )
            }
        }
    }

    private fun laasSingleOrMultipleDeltakelser(
        iderSomSkalLaases: Set<UUID>,
        nyDeltakerId: UUID,
    ) {
        if (iderSomSkalLaases.isEmpty()) return

        log.info(
            "Låser ${iderSomSkalLaases.size} deltakere for endringer grunnet nyere aktiv deltaker med id $nyDeltakerId",
        )

        if (iderSomSkalLaases.size > 1) {
            deltakerRepository.disableKanEndresMany(iderSomSkalLaases)
        } else {
            deltakerRepository.settKanEndres(iderSomSkalLaases.first(), false)
        }
    }

    private fun harEndretStatus(deltakeroppdatering: Deltakeroppdatering): Boolean {
        val currentStatus = DeltakerStatusRepository.getAktivDeltakerStatus(deltakeroppdatering.id) ?: return true

        return currentStatus.type != deltakeroppdatering.status.type
    }

    companion object {
    }
}
