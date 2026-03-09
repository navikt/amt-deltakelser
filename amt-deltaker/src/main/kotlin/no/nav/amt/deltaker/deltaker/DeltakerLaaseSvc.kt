package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

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

    /**
     * Sjekker om en gitt [deltaker] kan endres.
     *
     * En deltaker kan kun endres dersom den er den nyeste relevante deltakelsen
     * for personen i samme deltakerliste. Dersom det finnes flere deltakelser på
     * samme person, vil eldre deltakelser bli låst for endring.
     *
     * Logikken fungerer slik:
     * - Hvis personen kun har én deltakelse i deltakerlisten, kan den alltid endres.
     * - Hvis det finnes flere deltakelser, sorteres disse etter:
     *   1. Påmeldt-tidspunkt (nyeste først)
     *   2. Statusens `gyldigFra` (nyeste først)
     * - Deretter velges den nyeste deltakelsen med en aktiv status dersom en finnes,
     *   ellers velges den nyeste deltakelsen uavhengig av status.
     * - Kun denne nyeste deltakelsen kan endres. Alle eldre deltakelser anses som låst.
     *
     * Hvis [deltaker] ikke er den nyeste relevante deltakelsen, returneres `false`
     * og det logges at deltakeren er låst fordi en nyere deltakelse finnes.
     *
     * @param deltaker deltakeren som vurderes for endring
     * @return `true` dersom deltakeren kan endres, ellers `false`
     */
    fun erLaastForEndringer(deltaker: Deltaker): Boolean {
        val deltakelserForPerson = deltakerRepository.getFlereForPerson(
            personIdent = deltaker.navBruker.personident,
            deltakerlisteId = deltaker.deltakerliste.id,
        )

        require(deltakelserForPerson.any()) { "Fant ingen deltakelser i deltakerliste med deltaker-id ${deltaker.id}" }

        // hvis det kun finnes en deltakelse på personen, så skal den ikke være låst
        deltakelserForPerson.singleOrNull()?.let { return false }

        val deltakelserPaaPersonSorted = deltakelserForPerson
            .sortedWith(
                compareByDescending<Deltaker> { getPaameldtTidspunkt(it) }
                    .thenByDescending { it.status.gyldigFra },
            )

        val nyesteDeltakelse = deltakelserPaaPersonSorted
            .firstOrNull { it.status.type in AKTIVE_STATUSER }
            ?: deltakelserPaaPersonSorted.first()

        return if (deltaker.id != nyesteDeltakelse.id) {
            log.info("Deltaker er låst fordi det finnes en nyere deltakelse ${nyesteDeltakelse.id} på personen")
            true
        } else {
            false
        }
    }
}
