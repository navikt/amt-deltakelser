package no.nav.amt.deltaker.digitalbruker

import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient

/**
 * DB-backet cache for `erDigital`-oppslag. Reduserer antall HTTP-kall til `amt-distribusjon`
 * ved å lagre resultatet i [digital_bruker_cache]-tabellen med 24-timers TTL.
 *
 * Bruksmønster for bulk-kall (tiltakskoordinator-lista med >2000 deltakere):
 *  1. Hent ferske entries (< 24 timer) fra DB i ett oppslag
 *  2. Hent kun manglende/utdaterte fra `amt-distribusjon`
 *  3. Oppdater DB med ferske verdier
 *  4. Returner komplett map
 */
class DigitalBrukerService(
    private val amtDistribusjonClient: AmtDistribusjonClient,
) {
    /**
     * Henter `erDigital` for et sett med personidenter. Bruker DB-cache med 24-timers TTL
     * for å minimere HTTP-kall. Manglende eller utdaterte entries hentes fra `amt-distribusjon`
     * og oppdateres i databasen.
     */
    suspend fun hentErDigitalForPersonidenter(personidenter: Set<String>): Map<String, Boolean> {
        if (personidenter.isEmpty()) return emptyMap()

        val cached = DigitalBrukerCacheRepository.hentForPersonidenter(personidenter)

        val manglendeEllerUtdaterte = personidenter - cached.keys

        val hentetFraKlient = manglendeEllerUtdaterte.map { personident ->
            personident to amtDistribusjonClient.digitalBruker(personident)
        }

        if (hentetFraKlient.isNotEmpty()) {
            DigitalBrukerCacheRepository.upsertBatch(hentetFraKlient)
        }

        val ferskeMap = cached.mapValues { (_, entry) -> entry.erDigital }
        val hentetMap = hentetFraKlient.toMap()

        return ferskeMap + hentetMap
    }

    /**
     * Henter `erDigital` for én enkelt personident. Bruker samme cache som bulk-varianten.
     * Hvis verdien ikke kan hentes (uventet) returneres `false`.
     */
    suspend fun erDigital(personident: String): Boolean = hentErDigitalForPersonidenter(setOf(personident))[personident] ?: false
}
