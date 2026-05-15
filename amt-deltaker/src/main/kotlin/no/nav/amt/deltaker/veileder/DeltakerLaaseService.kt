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
 * Alle oppslag går gjennom samme spissede SQL-spørring
 * ([DeltakerRepository.getDeltakelserForLaaseSjekk]) — både for enkelt-deltaker- og bulk-varianten.
 */
class DeltakerLaaseService(
    private val deltakerRepository: DeltakerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Sjekker om en [deltaker] er låst for endringer.
     *
     * Tynn wrapper rundt [erLaastForEndringerForDeltakere] med én deltaker.
     *
     * @return `true` dersom deltakeren er låst, ellers `false`
     */
    fun erLaastForEndringer(deltaker: Deltaker): Boolean = erLaastForEndringerForDeltakere(listOf(deltaker)).getValue(deltaker.id)

    /**
     * Bulk-variant. Beregner låsing for alle [deltakere] i samme deltakerliste i én spisset
     * SQL-spørring (slim SELECT, ingen JOIN til deltakerliste/arrangør/tiltak/vedtaksdetaljer).
     * Egnet for store kall som tiltakskoordinator-lista.
     *
     * Forutsetter at alle deltakere hører til **samme deltakerliste**.
     *
     * @return Map fra deltaker-id til hvorvidt deltakeren er låst for endringer.
     */
    fun erLaastForEndringerForDeltakere(deltakere: List<Deltaker>): Map<UUID, Boolean> {
        if (deltakere.isEmpty()) return emptyMap()

        val deltakerlisteIder = deltakere.map { it.deltakerliste.id }.toSet()
        require(deltakerlisteIder.size == 1) {
            "Alle deltakere må høre til samme deltakerliste (fant ${deltakerlisteIder.size})"
        }
        val deltakerlisteId = deltakerlisteIder.first()

        val personIdenter = deltakere.map { it.navBruker.personident }.toSet()
        val deltakelserPerPerson = deltakerRepository.getDeltakelserForLaaseSjekk(personIdenter, deltakerlisteId)

        return deltakere.associate { deltaker ->
            val deltakelserForPerson = deltakelserPerPerson[deltaker.navBruker.personident].orEmpty()
            require(deltakelserForPerson.any()) {
                "Fant ingen deltakelser i deltakerliste med deltaker-id ${deltaker.id}"
            }
            deltaker.id to erLaastForEndringer(deltaker.id, deltakelserForPerson)
        }
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
