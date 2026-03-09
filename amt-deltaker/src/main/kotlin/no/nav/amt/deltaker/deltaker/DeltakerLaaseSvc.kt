package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Tjeneste som avgjør om en [Deltaker] er låst for endringer.
 *
 * Kun den nyeste relevante deltakelsen for en person i en deltakerliste kan
 * endres. Eldre deltakelser låses for å bevare historikk.
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
     * Sjekker om en [deltaker] er låst for endringer.
     *
     * Hvis personen har flere deltakelser i samme deltakerliste, anses kun den
     * nyeste relevante deltakelsen som redigerbar. Alle tidligere deltakelser
     * er låst.
     *
     * @return `true` dersom deltakeren er låst, ellers `false`
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
