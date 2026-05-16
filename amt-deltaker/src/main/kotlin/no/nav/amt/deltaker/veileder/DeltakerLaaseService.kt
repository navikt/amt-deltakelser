package no.nav.amt.deltaker.veileder

import no.nav.amt.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakelseLaaseInfo
import no.nav.amt.deltaker.repository.DeltakerRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Tjeneste som avgjør om en [Deltaker] er låst for endringer.
 *
 * Kun den nyeste relevante deltakelsen for en person i en deltakerliste kan
 * endres. Eldre deltakelser låses for å bevare historikk.
 *
 * Slår opp i samme spissede SQL-spørring ([DeltakerRepository.getDeltakelserForLaaseSjekk])
 * — slim SELECT uten JOIN til deltakerliste/arrangør/tiltak/vedtaksdetaljer.
 */
class DeltakerLaaseService(
    private val deltakerRepository: DeltakerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Sjekker om en [deltaker] er låst for endringer.
     *
     * @return `true` dersom deltakeren er låst, ellers `false`
     */
    fun erLaastForEndringer(deltaker: Deltaker): Boolean {
        val deltakelser = deltakerRepository.getDeltakelserForLaaseSjekk(
            personident = deltaker.navBruker.personident,
            deltakerlisteId = deltaker.deltakerliste.id,
        )
        require(deltakelser.isNotEmpty()) {
            "Fant ingen deltakelser i deltakerliste med deltaker-id ${deltaker.id}"
        }
        return erLaastForEndringer(deltaker.id, deltakelser)
    }

    private fun erLaastForEndringer(
        deltakerId: UUID,
        deltakelserForPerson: List<DeltakelseLaaseInfo>,
    ): Boolean {
        // hvis det kun finnes en deltakelse på personen, så skal den ikke være låst
        deltakelserForPerson.singleOrNull()?.let { return false }

        val sortert = deltakelserForPerson
            .sortedWith(
                compareByDescending<DeltakelseLaaseInfo> {
                    paameldtTidspunkt(it.vedtakFattet, it.innsoektDatoFraArena)
                }.thenByDescending { it.statusGyldigFra },
            )

        val nyesteDeltakelse = sortert
            .firstOrNull { it.statusType in AKTIVE_STATUSER }
            ?: sortert.first()

        return if (deltakerId != nyesteDeltakelse.id) {
            log.info("Deltaker er låst fordi det finnes en nyere deltakelse ${nyesteDeltakelse.id} på personen")
            true
        } else {
            false
        }
    }

    private fun paameldtTidspunkt(
        vedtakFattet: LocalDateTime?,
        innsoektDatoFraArena: LocalDate?,
    ): LocalDateTime? = listOfNotNull(
        vedtakFattet,
        innsoektDatoFraArena?.atStartOfDay(),
    ).maxOrNull()
}
