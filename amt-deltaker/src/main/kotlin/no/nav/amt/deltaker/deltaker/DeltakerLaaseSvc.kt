package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
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
     */
    fun laasOppForrigeDeltaker(gjeldendeDeltaker: Deltaker): UUID? {
        val forrigeDeltaker = deltakerRepository
            .getFlereForPerson(
                personIdent = gjeldendeDeltaker.navBruker.personident,
                deltakerlisteId = gjeldendeDeltaker.deltakerliste.id,
            ).filter { it.id != gjeldendeDeltaker.id } // fjern gjeldende deltaker
            .mapNotNull { deltaker -> getPameldTidspunkt(deltaker)?.let { deltaker to it } } // hent påmeldingstidspunkt
            .maxByOrNull { it.second } // finn nyeste deltakelse
            ?.first
            ?: return null

        return if (forrigeDeltaker.skalLaasesOpp()) {
            deltakerRepository.settKanEndres(forrigeDeltaker.id, true)
            log.info(
                "Har låst opp tidligere deltaker ${forrigeDeltaker.id} for endringer pga. avbrutt utkast på nåværende deltaker",
            )
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
    internal fun getPameldTidspunkt(deltaker: Deltaker): LocalDateTime? = listOfNotNull(
        deltaker.vedtaksinformasjon?.fattet,
        importertFraArenaRepository
            .getForDeltaker(deltaker.id)
            ?.deltakerVedImport
            ?.innsoktDato
            ?.atStartOfDay(),
    ).maxOrNull()

    companion object {
        private fun Deltaker.skalLaasesOpp(): Boolean = status.type != DeltakerStatus.Type.FEILREGISTRERT &&
            status.type != DeltakerStatus.Type.AVBRUTT_UTKAST &&
            status.aarsak?.type != DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }
}
