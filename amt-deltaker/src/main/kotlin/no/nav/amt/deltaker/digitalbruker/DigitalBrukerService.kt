package no.nav.amt.deltaker.digitalbruker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient

/**
 * DB-backet cache for `erDigital`-oppslag. Reduserer antall HTTP-kall til `amt-distribusjon`
 * ved å lagre resultatet i [digital_bruker_cache]-tabellen med 24-timers TTL.
 *
 * Bruksmønster for bulk-kall (tiltakskoordinator-lista med >2000 deltakere):
 *  1. Hent ferske entries (< 24 timer) fra DB i ett oppslag
 *  2. Hent kun manglende/utdaterte fra `amt-distribusjon` i parallell (begrenset av [MAX_PARALLEL_HTTP_CALLS])
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
     *
     * HTTP-kallene til `amt-distribusjon` kjøres i parallell, men begrenset av en lokal
     * [Semaphore] til [MAX_PARALLEL_HTTP_CALLS] samtidige kall for å unngå å overbelaste
     * `amt-distribusjon` ved kald cache (f.eks. like etter deploy med 2000+ deltakere).
     */
    suspend fun hentErDigitalForPersonidenter(personidenter: Set<String>): Map<String, Boolean> {
        if (personidenter.isEmpty()) return emptyMap()

        val cached = DigitalBrukerCacheRepository.hentForPersonidenter(personidenter)

        val manglendeEllerUtdaterte = personidenter - cached.keys

        val hentetFraKlient = if (manglendeEllerUtdaterte.isEmpty()) {
            emptyList()
        } else {
            val semaphore = Semaphore(permits = MAX_PARALLEL_HTTP_CALLS)
            coroutineScope {
                manglendeEllerUtdaterte
                    .map { personident ->
                        async {
                            semaphore.withPermit {
                                personident to amtDistribusjonClient.digitalBruker(personident)
                            }
                        }
                    }.awaitAll()
            }
        }

        if (hentetFraKlient.isNotEmpty()) {
            DigitalBrukerCacheRepository.upsertBatch(hentetFraKlient)
        }

        return cached + hentetFraKlient.toMap()
    }

    /**
     * Henter `erDigital` for én enkelt personident. Bruker samme cache som bulk-varianten.
     * Returnerer `false` dersom oppslaget ikke inneholder en verdi for personidenten.
     *
     * Eventuelle feil ved henting fra `amt-distribusjon` håndteres ikke her, men bobler opp
     * til kallende kode.
     */
    suspend fun erDigital(personident: String): Boolean = hentErDigitalForPersonidenter(setOf(personident))[personident] ?: false

    companion object {
        /**
         * Maks antall samtidige HTTP-kall mot `amt-distribusjon` per `hentErDigitalForPersonidenter`-kall.
         * Begrenser belastningen på `amt-distribusjon` ved kald cache.
         */
        private const val MAX_PARALLEL_HTTP_CALLS = 20
    }
}
