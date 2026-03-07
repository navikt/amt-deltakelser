package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.tiltakskoordinator.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

/**
 * Tjeneste for å håndtere låsing og oppheving av lås på deltakere.
 *
 * Hovedfunksjonaliteten er å låse opp tidligere deltakere for endringer
 * basert på påmeldingstidspunkt og status, typisk når et utkast for en
 * gjeldende deltaker er avbrutt.
 */
class DeltakerLaaseSvc(
    private val deltakerRepository: DeltakerRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Låser opp den nyeste tidligere deltakeren for endringer dersom det finnes en,
     * basert på påmeldingstidspunkt.
     *
     * Metoden finner tidligere deltakere for samme person (unntatt gjeldende deltaker),
     * henter deres påmeldingstidspunkt, og velger den med nyeste tidspunkt.
     * Hvis denne deltakeren kan låses opp, settes den som redigerbar i databasen.
     *
     * @param gjeldendeDeltaker Deltakeren som utløser opphevingen av lås på en tidligere deltaker.
     * @return id til deltakeren som er låst opp, eller `null` hvis ingen deltaker ble låst opp.
     */
    fun laasOppForrigeDeltaker(gjeldendeDeltaker: Deltaker): UUID? {
        val forrigeDeltaker = deltakerRepository
            .getFlereForPerson(
                personIdent = gjeldendeDeltaker.navBruker.personident,
                deltakerlisteId = gjeldendeDeltaker.deltakerliste.id,
            ).filterNot { it.id == gjeldendeDeltaker.id } // fjern gjeldende deltaker
            .mapNotNull { deltaker -> getPaameldtTidspunkt(deltaker)?.let { deltaker to it } } // hent påmeldingstidspunkt
            .maxByOrNull { it.second } // finn nyeste deltakelse
            ?.first
            ?: return null

        return if (forrigeDeltaker.skalLaasesOpp()) {
            deltakerRepository.settKanEndres(forrigeDeltaker.id, true)
            log.info("Har låst opp tidligere deltaker ${forrigeDeltaker.id} for endringer pga. avbrutt utkast på nåværende deltaker")
            forrigeDeltaker.id
        } else {
            null
        }
    }

    /**
     * Henter tidspunktet for når en deltaker ble påmeldt.
     *
     * Tidspunktet bestemmes som den nyeste av:
     * 1. Sist endret-dato for vedtak.
     * 2. Innsøktsdato fra Arena-import (omgjort til start på dagen).
     *
     * @param deltaker deltaker som skal sjekkes.
     * @return Nyeste påmeldingstidspunkt, eller `null` hvis ingen datoer er tilgjengelige.
     */
    fun getPaameldtTidspunkt(deltaker: Deltaker): LocalDateTime? = listOfNotNull(
        deltaker.vedtaksinformasjon?.fattet,
        importertFraArenaRepository
            .getForDeltaker(deltaker.id)
            ?.deltakerVedImport
            ?.innsoktDato
            ?.atStartOfDay(),
    ).maxOrNull()

    // benyttes av DeltakerV2Consumer
    fun oppdaterDeltakerLaas(
        deltakerId: UUID,
        personident: String,
        deltakerlisteId: UUID,
    ) {
        fun laasOppDeltakelse(deltakerId: UUID) {
            log.info("Nyeste deltakelse $deltakerId var låst for endringer. Låser opp")
            deltakerRepository.settKanEndres(deltakerId, true)
        }

        /*
            Denne funksjonen er ment til alle scenarioer hvor det er relevant med låsing av deltakere.
            Skal kalles etter at databasen er oppdatert med ny/oppdatering av deltaker som gjør at den er vanskelig å gjenbruke noen steder
            (flere steder så oppdateres ikke databasen med deltakerendringer før resultatet er mottatt på kafka).
            OBS: Flere deltakelser kan ha samme påmeldt dato(i tilfelle den ene er historisert).
            Scenario 1: oppdatering av eksisterende deltakelser
            Scenario 2: Import av data fra Arena
            Scenario 3: Avbryt utkast som i praksis vil ha en deltakelse uten påmeldtdato
         */
        val deltakelserPaaPerson = deltakerRepository.getFlereForPerson(
            personIdent = personident,
            deltakerlisteId = deltakerlisteId,
        )

        if (deltakelserPaaPerson.none { it.id == deltakerId }) {
            throw IllegalStateException("Den nye deltakelsen $deltakerId må være upsertet for å bruke denne funksjonen")
        }

        // early-return hvis kun en deltakelse
        deltakelserPaaPerson.singleOrNull()?.let { deltakelse ->
            if (!deltakelse.kanEndres) {
                laasOppDeltakelse(deltakelse.id)
            }
            return
        }

        val deltakelserPaaPersonSorted = deltakelserPaaPerson
            .sortedWith(
                compareByDescending<Deltaker> { getPaameldtTidspunkt(it) }
                    .thenByDescending { it.status.gyldigFra },
            )

        val nyesteDeltakelse = deltakelserPaaPersonSorted
            .firstOrNull { it.status.type in AKTIVE_STATUSER }
            ?: deltakelserPaaPersonSorted.first()

        if (deltakerId != nyesteDeltakelse.id) {
            log.info("Fikk oppdatering på $deltakerId som skal låses fordi det er nyere deltakelse ${nyesteDeltakelse.id} på personen")
        }

        val deltakelserSomSkalLaases = deltakelserPaaPersonSorted
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
            laasOppDeltakelse(nyesteDeltakelse.id)
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
        fun Deltaker.skalLaasesOpp(): Boolean = status.type != DeltakerStatus.Type.FEILREGISTRERT &&
            status.type != DeltakerStatus.Type.AVBRUTT_UTKAST &&
            status.aarsak?.type != DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }
}
